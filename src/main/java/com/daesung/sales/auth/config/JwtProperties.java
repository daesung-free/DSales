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
        Boolean cookieSecure,
        String cookieSameSite
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

    /**
     * refresh 쿠키 SameSite. 기본 {@code Strict}.
     *
     * <p>★<b>프론트와 API가 다른 도메인이면 {@code None}이어야 한다.</b>
     * 프론트가 Amplify(`*.amplifyapp.com`), API가 다른 호스트면 브라우저가 이를 교차 사이트로 보고
     * {@code Strict}·{@code Lax} 쿠키를 <b>아예 보내지 않는다</b>. 그러면 CORS를 열어도
     * 로그인 직후는 되다가 access 토큰이 만료되는 30분 뒤 재발급이 실패해 로그아웃된다 —
     * 원인을 찾기 어려운 형태로 깨진다.
     *
     * <p>‼️{@code None}은 <b>Secure 없이는 브라우저가 거부</b>한다. 즉 API가 https여야 한다.
     * 그래서 {@link #assertCookiePolicy()}로 기동 시점에 조합을 검사한다.
     */
    public String cookieSameSiteOrDefault() {
        return (cookieSameSite == null || cookieSameSite.isBlank()) ? "Strict" : cookieSameSite.trim();
    }

    /**
     * 쿠키 정책 정합성 검사. <b>기동 시점에</b> 잡는다 —
     * 런타임에 드러나면 "가끔 로그아웃된다"는 증상으로만 보여 원인을 못 찾는다.
     */
    public void assertCookiePolicy() {
        // 로케일을 명시한다 — 터키어 로케일에서 i→İ 로 접혀 "NONE" 비교가 어긋나는 것을 막는다.
        String mode = cookieSameSiteOrDefault().toUpperCase(java.util.Locale.ROOT);
        if ("NONE".equals(mode) && !cookieSecureOrDefault()) {
            throw new IllegalStateException(
                    "SameSite=None 쿠키는 Secure가 필수입니다(브라우저가 거부). "
                            + "API를 https로 올리고 daesung.security.cookie-secure=true 로 함께 켜세요.");
        }
    }
}
