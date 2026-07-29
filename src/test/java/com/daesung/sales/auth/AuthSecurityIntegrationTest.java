package com.daesung.sales.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * 인증 보안 강화 통합테스트: refresh httpOnly 쿠키 + 재발급 회전 + 재사용(탈취) 탐지.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("인증 보안(쿠키·재사용탐지) 통합테스트")
class AuthSecurityIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void seedUser() {
        // 관리자 권한으로 전용 테스트 계정 생성(family-revoke를 admin과 격리)
        exchangeRaw(HttpMethod.POST, "/auth/users", Map.of(
                "username", "reuse-user", "password", "Reuse1234!", "name", "재사용테스트", "role", "VIEWER"),
                token(), null);
        // RBAC 검증용 역할별 계정
        createUser("rbac-finance", "FINANCE");
        createUser("rbac-sales", "SALES");
        createUser("rbac-viewer", "VIEWER");
    }

    private void createUser(String username, String role) {
        exchangeRaw(HttpMethod.POST, "/auth/users", Map.of(
                "username", username, "password", "Pw123456!", "name", username, "role", role), token(), null);
    }

    /** 해당 계정 로그인 → access 토큰. */
    private String loginToken(String username) throws Exception {
        var resp = exchangeRaw(HttpMethod.POST, "/auth/login",
                Map.of("username", username, "password", "Pw123456!"), null, null);
        return om.readTree(resp.getBody()).path("data").path("accessToken").asText();
    }

    private int status(HttpMethod method, String path, Object body, String bearer) {
        return exchangeRaw(method, path, body, bearer, null).getStatusCode().value();
    }

    @Test
    @DisplayName("RBAC — 마감/세무는 FINANCE, 매출등록은 SALES, 조회는 공통")
    void rbac매트릭스() throws Exception {
        String finance = loginToken("rbac-finance");
        String sales = loginToken("rbac-sales");
        String viewer = loginToken("rbac-viewer");

        // 마감/세무 조회: FINANCE 200, VIEWER 403(민감 재무데이터)
        assertThat(status(HttpMethod.GET, "/closing/revenue-report", null, finance)).isEqualTo(200);
        assertThat(status(HttpMethod.GET, "/closing/revenue-report", null, viewer)).isEqualTo(403);

        // 매출등록(POST): SALES 계열만. VIEWER 403(권한부족, 바디 무관)
        assertThat(status(HttpMethod.POST, "/sales/entries", Map.of(), viewer)).isEqualTo(403);

        // 마스터 쓰기(POST): ADMIN만. SALES 403
        assertThat(status(HttpMethod.POST, "/masters/clients",
                Map.of("code", "X", "name", "X", "type", "NORMAL"), sales)).isEqualTo(403);

        // 매출목표(대시보드) 등록: 영업/재무만. VIEWER 403
        assertThat(status(HttpMethod.POST, "/dashboard/targets", Map.of(), viewer)).isEqualTo(403);
        // 물류단가 수정: 물류만. VIEWER 403
        assertThat(status(HttpMethod.PUT, "/logistics-costs/rates/1", Map.of(), viewer)).isEqualTo(403);

        // 일반 조회는 VIEWER도 200
        assertThat(status(HttpMethod.GET, "/sales/statement?from=2026-06-01&to=2026-06-30", null, viewer))
                .isEqualTo(200);

        // /auth/me 역할 반영
        var me = om.readTree(exchangeRaw(HttpMethod.GET, "/auth/me", null, finance, null).getBody());
        assertThat(me.path("data").path("role").asText()).isEqualTo("FINANCE");
    }

    @Test
    @DisplayName("로그인 — refresh는 바디에 없고 httpOnly 쿠키로 발급")
    void 로그인_쿠키() throws Exception {
        ResponseEntity<String> resp = login();
        JsonNode body = om.readTree(resp.getBody());
        // access는 바디, refresh는 바디에 없음(null/생략)
        assertThat(body.path("data").path("accessToken").asText()).isNotBlank();
        assertThat(body.path("data").path("refreshToken").asText("")).isEmpty();
        // Set-Cookie에 httpOnly refresh 쿠키
        String setCookie = firstRefreshSetCookie(resp);
        assertThat(setCookie).contains("refresh_token=").contains("HttpOnly");
    }

    @Test
    @DisplayName("재사용 탐지 — 회전된 옛 refresh 재사용 시 전체 세션 무효화")
    void 재사용탐지() throws Exception {
        String cookie1 = refreshCookieValue(login());       // 최초 refresh

        // 1차 재발급(정상 회전) → 새 refresh(cookie2)
        ResponseEntity<String> r1 = exchangeRaw(HttpMethod.POST, "/auth/refresh", null, null, cookie1);
        assertThat(r1.getStatusCode().is2xxSuccessful()).isTrue();
        String cookie2 = refreshCookieValue(r1);

        // 옛 refresh(cookie1) 재사용 → 재사용 탐지로 401 + 전체 무효화
        ResponseEntity<String> reuse = exchangeRaw(HttpMethod.POST, "/auth/refresh", null, null, cookie1);
        assertThat(reuse.getStatusCode().value()).isEqualTo(401);
        assertThat(om.readTree(reuse.getBody()).path("error").path("message").asText()).contains("재사용");

        // family-revoke로 정상 회전본(cookie2)도 무효화됨
        ResponseEntity<String> after = exchangeRaw(HttpMethod.POST, "/auth/refresh", null, null, cookie2);
        assertThat(after.getStatusCode().value()).isEqualTo(401);
    }

    // ── helpers ──

    private ResponseEntity<String> login() {
        return exchangeRaw(HttpMethod.POST, "/auth/login",
                Map.of("username", "reuse-user", "password", "Reuse1234!"), null, null);
    }

    /** 응답 Set-Cookie 중 refresh_token 헤더 원문. */
    private String firstRefreshSetCookie(ResponseEntity<String> resp) {
        var cookies = resp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(cookies).isNotNull();
        return cookies.stream().filter(c -> c.startsWith("refresh_token=")).findFirst().orElseThrow();
    }

    /** 다음 요청에 보낼 "refresh_token=값" 쿠키 문자열. */
    private String refreshCookieValue(ResponseEntity<String> resp) {
        String setCookie = firstRefreshSetCookie(resp);
        return setCookie.split(";", 2)[0];   // "refresh_token=xxxx"
    }
}
