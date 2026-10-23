package com.daesung.sales.auth;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * 서버 기본 권한을 발주처 매트릭스까지 좁힌 것(2026-10-22 프론트 실측 ③⑦·⑥).
 *
 * <p>★<b>화면만 잠그는 것은 잠근 것이 아니다.</b> 프론트는 매트릭스대로 메뉴를 감췄는데
 * 요청을 직접 보내면 서버가 받고 있었다 — 서버가 기본값을 들고 있어야 한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("권한 축소 · 만료 후 로그아웃")
class PermissionTighteningIntegrationTest extends IntegrationTestSupport {

    private static final String PW = "Pw123456!";
    private static final String SFX = "pt" + (System.nanoTime() % 1_000_000L);

    @BeforeAll
    void seedUsers() {
        token();
        create("sales-" + SFX, "SALES");
        create("logis-" + SFX, "LOGISTICS");
        create("fin-" + SFX, "FINANCE");
    }

    private void create(String username, String role) {
        exchangeRaw(HttpMethod.POST, "/auth/users", Map.of(
                "username", username, "password", PW, "name", username, "role", role), token(), null);
    }

    private String login(String username) throws Exception {
        ResponseEntity<String> r = exchangeRaw(HttpMethod.POST, "/auth/login",
                Map.of("username", username, "password", PW), null, null);
        return om.readTree(r.getBody()).path("data").path("accessToken").asText();
    }

    private int status(HttpMethod m, String path, Object body, String bearer) {
        return exchangeRaw(m, path, body, bearer, null).getStatusCode().value();
    }

    @Test
    @DisplayName("영업은 매출관리 그룹을 **조회만** 한다 — 매트릭스 「영업 × 매출관리 = ◐」")
    void 영업은_매출관리_조회만() throws Exception {
        String sales = login("sales-" + SFX);

        // 조회는 열려 있다
        assertThat(status(HttpMethod.GET, "/sales?page=0&size=1", null, sales))
                .as("매출 조회").isEqualTo(200);

        // ‼️쓰기는 막힌다. 예전엔 V52 의 `ELSE WRITE` 때문에 매출·위탁·정산이 전부 열려 있었다.
        //   본문이 틀려도 상관없다 — 권한에서 먼저 걸려야 한다(400 이 나오면 통과한 것이다).
        assertThat(status(HttpMethod.POST, "/sales/entries", Map.of(), sales))
                .as("매출 등록은 막힌다").isEqualTo(403);
        assertThat(status(HttpMethod.POST, "/consignment/settle", Map.of(), sales))
                .as("위탁 정산도 같은 그룹이다").isEqualTo(403);

        // 주문/출고관리는 매트릭스에서 영업 ○ 다 — 같이 막히면 안 된다.
        assertThat(status(HttpMethod.GET, "/orders/branches", null, sales))
                .as("주문·출고는 영업에게 열려 있다").isNotEqualTo(403);
    }

    @Test
    @DisplayName("개인회원 조회는 관리자만 — 학생이름·주소·연락처가 들어가는 자리다")
    void 개인회원은_관리자만() throws Exception {
        String path = "/masters/person-members?page=0&size=1";

        // ‼️예전엔 /masters/** 에 묻혀 세 역할 모두 200 이었다.
        assertThat(status(HttpMethod.GET, path, null, login("sales-" + SFX))).isEqualTo(403);
        assertThat(status(HttpMethod.GET, path, null, login("logis-" + SFX))).isEqualTo(403);
        assertThat(status(HttpMethod.GET, path, null, login("fin-" + SFX))).isEqualTo(403);

        assertThat(status(HttpMethod.GET, path, null, token())).as("관리자는 본다").isEqualTo(200);

        // 같은 /masters 아래 다른 화면까지 막히면 과하게 좁힌 것이다 — 경계가 맞는지 함께 본다.
        assertThat(status(HttpMethod.GET, "/masters/clients?page=0&size=1", null, login("sales-" + SFX)))
                .as("거래처는 그대로 조회된다").isEqualTo(200);
    }

    @Test
    @DisplayName("★access 토큰 없이도 로그아웃된다 — 만료 뒤 재로그인 쿠키가 남으면 안 된다")
    void 토큰_없어도_로그아웃() throws Exception {
        // 로그인해서 refresh 쿠키를 받는다
        ResponseEntity<String> loginResp = exchangeRaw(HttpMethod.POST, "/auth/login",
                Map.of("username", "fin-" + SFX, "password", PW), null, null);
        List<String> setCookies = loginResp.getHeaders().get(HttpHeaders.SET_COOKIE);
        assertThat(setCookies).isNotNull();
        String cookie = setCookies.stream().filter(c -> c.startsWith("refresh_token="))
                .findFirst().orElseThrow().split(";")[0];

        // ‼️access 토큰을 **안 보낸다**(만료된 상황과 같다). 예전엔 여기서 401 이 나고 쿠키가 남았다.
        ResponseEntity<String> out = exchangeRaw(HttpMethod.POST, "/auth/logout", null, null, cookie);
        assertThat(out.getStatusCode().value()).as("만료 상태에서도 로그아웃된다").isEqualTo(200);

        // 쿠키 삭제 지시가 나가야 한다
        assertThat(out.getHeaders().get(HttpHeaders.SET_COOKIE))
                .as("쿠키 삭제 지시").anyMatch(c -> c.startsWith("refresh_token=;")
                        || c.contains("refresh_token=;") || c.contains("Max-Age=0"));

        // ★그 쿠키로는 더 이상 재발급되지 않는다 — 이게 진짜 보장이다.
        assertThat(exchangeRaw(HttpMethod.POST, "/auth/refresh", null, null, cookie)
                .getStatusCode().is2xxSuccessful())
                .as("폐기된 세션은 되살아나지 않는다").isFalse();
    }
}
