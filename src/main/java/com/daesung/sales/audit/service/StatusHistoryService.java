package com.daesung.sales.audit.service;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.entity.StatusHistory;
import com.daesung.sales.audit.repository.StatusHistoryRepository;
import com.daesung.sales.common.audit.CurrentAuditor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 상태변경 이력 기록. 근거: 개발문서 5.0 / 리스크 12.0(보안심사 직결).
 *
 * <p><b>왜 JPA 자동감지(@PostUpdate)가 아니라 명시 호출인가</b>
 * <ol>
 *   <li>자동은 "왜 바뀌었는지"를 남길 수 없다. 감사에서 실제로 묻는 건 사유다.</li>
 *   <li>우리 코드엔 JPA를 우회해 DB를 직접 고치는 자리가 있다(재고 원자적 UPDATE, 논리삭제 벌크).
 *       자동에만 의존하면 거기서 조용히 누락된다 — 빠지는 감사기록은 없느니만 못하다.</li>
 * </ol>
 * 대신 호출을 빠뜨릴 위험이 있으므로 상태 전이 지점마다 통합테스트로 고정한다.
 *
 * <p>기록은 <b>상태 변경과 같은 트랜잭션</b>에서 이뤄진다. 한쪽만 남으면 감사기록으로 쓸 수 없다.
 */
@Service
@RequiredArgsConstructor
public class StatusHistoryService {

    private final StatusHistoryRepository repository;
    private final CurrentAuditor currentAuditor;

    /**
     * 상태 변경 1건 기록. 호출 트랜잭션에 참여한다(REQUIRED 기본값).
     *
     * @param from 변경 <b>전</b> 값. 반드시 바꾸기 전에 읽어서 넘길 것 — 바꾼 뒤 읽으면 from==to가 된다.
     */
    @Transactional
    public void record(StatusEntityType entityType, Long entityId, String field,
                       String from, String to, String reason) {
        repository.save(StatusHistory.of(entityType, entityId, field,
                from, to, reason, currentAuditor.username()));
    }

    /** boolean 상태축 편의 메서드(취소·잠금·활성 등). */
    @Transactional
    public void record(StatusEntityType entityType, Long entityId, String field,
                       boolean from, boolean to, String reason) {
        record(entityType, entityId, field, String.valueOf(from), String.valueOf(to), reason);
    }
}
