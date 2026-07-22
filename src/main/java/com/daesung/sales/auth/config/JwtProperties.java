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
        long refreshTokenDays
) {
    public long accessTokenMinutesOrDefault() {
        return accessTokenMinutes > 0 ? accessTokenMinutes : 30;
    }

    public long refreshTokenDaysOrDefault() {
        return refreshTokenDays > 0 ? refreshTokenDays : 14;
    }
}
