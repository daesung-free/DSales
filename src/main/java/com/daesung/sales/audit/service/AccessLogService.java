package com.daesung.sales.audit.service;

import com.daesung.sales.audit.entity.AccessAction;
import com.daesung.sales.audit.entity.AccessLog;
import com.daesung.sales.audit.repository.AccessLogRepository;
import com.daesung.sales.common.query.MultiSelect;
import com.daesung.sales.common.response.PageResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 접근로그 기록·조회. */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccessLogService {

    private final AccessLogRepository repository;

    /**
     * 한 건 기록.
     *
     * <p>★<b>별도 트랜잭션</b>으로 남긴다({@code REQUIRES_NEW}). 업무 트랜잭션에 얹으면
     * 그쪽이 롤백될 때 기록도 함께 사라진다 — <b>실패한 시도야말로 남아야 하는 기록</b>이다.
     *
     * <p>★<b>여기서 나는 예외는 삼킨다.</b> 감사로그 때문에 사용자 요청이 실패하면 안 된다.
     * 대신 서버 로그에 남겨 기록이 빠진 사실 자체는 드러나게 한다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(AccessLog entry) {
        try {
            repository.save(entry);
        } catch (RuntimeException e) {
            // ★★로그에 **요청에서 온 값을 한 조각도 넣지 않는다.**
            //   경로·사용자명·쿼리는 전부 사용자가 보내는 값이라, 줄바꿈이 섞이면
            //   로그 한 줄을 통째로 위조할 수 있다(정적분석 CRLF_INJECTION_LOGS).
            //   여기서 알아야 할 것은 "기록이 빠졌다"는 사실 자체이고, 원인은 스택으로 충분하다.
            log.warn("접근로그 기록 실패 — 요청은 계속 진행합니다", e);
        }
    }

    @Transactional(readOnly = true)
    public PageResponse<AccessLogRow> search(LocalDateTime from, LocalDateTime to, String username,
                                             List<AccessAction> actions, boolean successOnly,
                                             Pageable pageable) {
        String kw = (username == null || username.isBlank())
                ? null : "%" + username.trim().toLowerCase(Locale.ROOT) + "%";
        return PageResponse.of(repository.search(from, to, kw,
                        MultiSelect.isAny(actions),
                        MultiSelect.orPlaceholder(actions, AccessAction.DOWNLOAD),
                        successOnly, pageable)
                .map(AccessLogRow::from));
    }

    /** 보존기간 경과분 정리. 반환값은 지운 건수. */
    @Transactional
    public int purgeOlderThan(LocalDateTime before) {
        return repository.deleteOlderThan(before);
    }

    /** 조회 행. 엔티티를 그대로 내보내지 않는다 — 기록 표는 밖에서 못 고치게 둔다. */
    public record AccessLogRow(Long id, String username, String role,
                               AccessAction action, String actionName, String menu,
                               String method, String path, String query,
                               int status, boolean success,
                               String fileName, Long fileSize,
                               String clientIp, Integer tookMs, LocalDateTime createdAt) {
        public static AccessLogRow from(AccessLog a) {
            return new AccessLogRow(a.getId(), a.getUsername(), a.getRole(),
                    a.getAction(), a.getAction().label(), a.getMenu(),
                    a.getMethod(), a.getPath(), a.getQuery(),
                    a.getStatus(), a.isSuccess(), a.getFileName(), a.getFileSize(),
                    a.getClientIp(), a.getTookMs(), a.getCreatedAt());
        }
    }
}
