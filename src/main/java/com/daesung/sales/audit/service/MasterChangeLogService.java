package com.daesung.sales.audit.service;

import com.daesung.sales.audit.dto.MasterChangeResponse;
import com.daesung.sales.audit.entity.MasterChangeLog;
import com.daesung.sales.audit.entity.MasterEntityType;
import com.daesung.sales.audit.repository.MasterChangeLogRepository;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.common.audit.CurrentAuditor;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 기초정보 변경 이력 기록·조회. 근거: 발주처 확정(자료요청서 3-1 라) —
 * "잘못 수정된 경우를 발견하기 위해 기초정보(거래처·상품·단가) 변경 이력도 포함".
 *
 * <p>기록 방식은 <b>스냅샷 비교</b>다. 각 엔티티가 {@code auditSnapshot()}으로 필드 맵을 내주고,
 * 수정 전후를 비교해 <b>달라진 필드만</b> 남긴다. 필드를 일일이 나열하지 않아 필드가 늘어도
 * 스냅샷 한 줄만 추가하면 되고, 무엇보다 "안 바뀐 항목이 이력에 쌓이는" 일이 없다.
 */
@Service
@RequiredArgsConstructor
public class MasterChangeLogService {

    private final MasterChangeLogRepository repository;
    private final CurrentAuditor currentAuditor;

    /**
     * 수정 전후 스냅샷을 비교해 달라진 필드만 기록한다.
     *
     * <p>호출 트랜잭션에 참여한다 — 본 작업이 롤백되면 이력도 함께 사라져야 한다
     * (일어나지 않은 변경이 이력에만 남으면 감사에서 더 나쁘다).
     *
     * @param before 수정 전 {@code auditSnapshot()}. <b>반드시 바꾸기 전에 떠야 한다</b> —
     *               JPA 영속 엔티티는 값을 바꾸면 같은 객체가 바뀌므로, 나중에 뜨면 before==after가 된다.
     */
    @Transactional
    public void recordDiff(MasterEntityType type, Long entityId, String entityCode,
                           Map<String, String> before, Map<String, String> after) {
        String actor = currentAuditor.username();
        List<MasterChangeLog> logs = new ArrayList<>();
        for (Map.Entry<String, String> e : after.entrySet()) {
            String key = e.getKey();
            String oldV = before.get(key);
            String newV = e.getValue();
            if (Objects.equals(oldV, newV)) {
                continue;
            }
            String[] parts = key.split("\\|", 2);   // "필드명|한글라벨"
            logs.add(MasterChangeLog.of(type, entityId, entityCode,
                    parts[0], (parts.length > 1) ? parts[1] : null, oldV, newV, actor));
        }
        if (!logs.isEmpty()) {
            repository.saveAll(logs);
        }
    }

    /** 이력 조회(대상·변경자·기간 필터). */
    @Transactional(readOnly = true)
    public PageResponse<MasterChangeResponse> search(MasterEntityType entityType, Long entityId,
                                                     String changedBy, LocalDate fromDate,
                                                     LocalDate toDate, Pageable pageable) {
        LocalDateTime from = (fromDate == null) ? null : fromDate.atStartOfDay();
        // toDate는 그날 하루를 포함해야 한다 — 종료일 00:00으로 자르면 당일 기록이 통째로 빠진다.
        LocalDateTime to = (toDate == null) ? null : toDate.plusDays(1).atStartOfDay();
        String actor = (changedBy == null || changedBy.isBlank()) ? null : changedBy.trim();
        return PageResponse.of(repository.search(entityType, entityId, actor, from, to, pageable)
                .map(MasterChangeResponse::from));
    }

    /** 조회용 목록(엑셀). */
    @Transactional(readOnly = true)
    public List<MasterChangeResponse> list(MasterEntityType entityType, Long entityId,
                                           String changedBy, LocalDate fromDate,
                                           LocalDate toDate, Pageable pageable) {
        return search(entityType, entityId, changedBy, fromDate, toDate, pageable).getContent();
    }

    /** 스냅샷 맵 생성 도우미. 키는 {@code "필드명|한글라벨"} 형식으로 넣는다. */
    public static Map<String, String> snapshot() {
        return new LinkedHashMap<>();
    }

    /** 값을 문자열로. null은 null 그대로 둔다(빈 문자열과 구분해야 "지웠다"를 알 수 있다). */
    public static String str(Object v) {
        return (v == null) ? null : String.valueOf(v);
    }
}
