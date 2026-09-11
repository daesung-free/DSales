package com.daesung.sales.audit.service;

import com.daesung.sales.audit.entity.AccessAction;
import com.daesung.sales.audit.entity.AccessLog;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 권한 부족(403)을 {@code access_log}에 남기고 403을 돌려준다.
 *
 * <p>★<b>왜 필터가 아니라 여기인가.</b> {@link AccessLogFilter}는 {@code @Order(Integer.MAX_VALUE)}라
 * 스프링 시큐리티 체인보다 <b>뒤</b>에 있다. 시큐리티가 막으면 체인이 그 자리에서 끊겨
 * 필터의 {@code doFilter}가 아예 호출되지 않는다 — 그래서 409·401은 남는데
 * <b>403만 한 줄도 안 남았다</b>(2026-09-11 점검에서 발견).
 * 필터를 시큐리티 앞으로 당기면 403은 잡히지만 그때는 {@code SecurityContext}가 비어 있어
 * <b>누가 시도했는지</b>를 잃는다. 거부 지점인 여기서는 인증정보가 아직 살아 있다.
 *
 * <p>★조회(GET)여도 남긴다. 일반 조회는 소음이라 안 남기지만 <b>막힌 조회는 소음이 아니다</b> —
 * 권한 없는 사람이 남의 다운로드 기록을 열어 보려 한 것이야말로 이 표에서 가장 봐야 할 기록이다.
 *
 * <p>★기록이 응답을 망가뜨리지 않는다. 여기서 나는 예외는 삼키고 서버 로그로만 알린다 —
 * 감사기록을 못 남겼다고 403을 500으로 바꾸면 더 나쁘다.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AccessDeniedLogger implements AccessDeniedHandler {

    private final AccessLogService accessLogService;

    @Override
    public void handle(HttpServletRequest req, HttpServletResponse res, AccessDeniedException ex)
            throws IOException {
        // ★상태부터 세운다. 기록보다 응답이 우선이다.
        // setStatus 사용(sendError 아님) — sendError는 ERROR 재디스패치를 유발하고,
        // 그 재디스패치엔 JWT 필터가 안 돌아 익명 재평가로 401이 덮어쓴다(SecurityConfig 주석 참고).
        res.setStatus(HttpStatus.FORBIDDEN.value());
        try {
            record(req);
        } catch (RuntimeException e) {
            log.warn("권한거부 기록 실패(응답은 정상 403)", e);
        }
    }

    private void record(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (path == null || !path.startsWith(AccessLogFilter.API)) {
            return;   // 정적 리소스·문서는 대상이 아니다
        }
        accessLogService.record(AccessLog.of(
                AccessLogFilter.username(), AccessLogFilter.role(),
                AccessAction.DENIED, AccessLogFilter.menuOf(path),
                req.getMethod(), AccessLogFilter.trim(path, 300),
                AccessLogFilter.safeQuery(req.getQueryString()),
                HttpStatus.FORBIDDEN.value(),
                null, null,
                AccessLogFilter.clientIp(req),
                AccessLogFilter.trim(req.getHeader(HttpHeaders.USER_AGENT), 300),
                null));
    }
}
