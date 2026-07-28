package com.daesung.sales.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * JWT 설정. daesung.security.* 로 주입. ★secret은 배포 시 환경변수/시크릿으로만 — 소스 커밋 금지(§9).
 * access는 짧게(분), refresh는 길게(일).
 */
@ConfigurationProperties(prefix = "daesung.security")
public record JwtProperties(
        String jwtSecret,
        long accessTokenMinutes,
        long refreshTokenDays,
        Boolean cookieSecure
) {
    public long accessTokenMinutesOrDefault() {
        return accessTokenMinutes > 0 ? accessTokenMinutes : 30;
    }

    public long refreshTokenDaysOrDefault() {
        return refreshTokenDays > 0 ? refreshTokenDays : 14;
    }

    /** refresh 쿠키 Secure 플래그. 기본 true(https). http 개발/현재 라이브는 yml에서 false로 override. */
    public boolean cookieSecureOrDefault() {
        return cookieSecure == null || cookieSecure;
    }
}
