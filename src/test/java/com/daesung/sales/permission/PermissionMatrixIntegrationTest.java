package com.daesung.sales.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpMethod;

/**
 * 권한 매트릭스(V52) 회귀 고정. 근거: 발주처 회신 2026-08-21 ① +
 * 드라이브 「사용자권한_구조_설계_예시(수정)」.
 *
 * <p>★여기서 지키는 것은 <b>"관리자가 운영 중에 바꿀 수 있다"</b>는 요구다.
 * 원문: "화면/필드 단위 권한을 관리자가 운영 중 자유롭게 조정할 수 있는 구조가 필요합니다."
 * 배포 없이 바뀌지 않으면 이 요구를 만족하지 못한 것이다 —
 * 그래서 <b>권한을 바꾼 직후 같은 요청의 결과가 달라지는지</b>를 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("권한 매트릭스(관리자 운영 중 변경)")
class PermissionMatrixIntegrationTest extends IntegrationTestSupport {

    private String logisToken;

    @BeforeAll
    void seed() throws Exception {
        token();
        exchangeRaw(HttpMethod.POST, "/auth/users", Map.of(
                "username", "perm-logis", "password", "Pw123456!",
                "name", "권한물류", "role", "LOGISTICS"), token(), null);
        var resp = exchangeRaw(HttpMethod.POST, "/auth/login",
                Map.of("username", "perm-logis", "password", "Pw123456!"), null, null);
        logisToken = om.readTree(resp.getBody()).path("data").path("accessToken").asText();
    }

    private int status(HttpMethod m, String path, Object body, String bearer) {
        return exchangeRaw(m, path, body, bearer, null).getStatusCode().value();
    }

    private long screenId(String code) {
        for (JsonNode s : data(get("/permissions/screens"))) {
            if (code.equals(s.path("code").asText())) {
                return s.path("screenId").asLong();
            }
        }
        throw new AssertionError("화면 없음: " + code);
    }

    @Test
    @DisplayName("매트릭스가 ○/◐/– 표기로 나온다")
    void 매트릭스_조회() {
        JsonNode screens = data(get("/permissions/screens"));
        assertThat(screens).isNotEmpty();

        JsonNode closing = null;
        for (JsonNode s : screens) {
            if ("CLOSING".equals(s.path("code").asText())) {
                closing = s;
            }
        }
        assertThat(closing).isNotNull();
        for (JsonNode r : closing.path("roles")) {
            String role = r.path("role").asText();
            String mark = r.path("mark").asText();
            if ("FINANCE".equals(role) || "ADMIN".equals(role)) {
                assertThat(mark).as("마감은 재무·관리자가 쓴다").isEqualTo("○");
            }
            if ("LOGISTICS".equals(role)) {
                assertThat(mark).as("물류에게 마감은 비노출").isEqualTo("–");
            }
        }
    }

    @Test
    @DisplayName("★관리자가 권한을 바꾸면 배포 없이 곧바로 적용된다")
    void 운영중_변경() {
        long closingId = screenId("CLOSING");

        // 처음엔 물류가 마감을 못 본다(–)
        assertThat(status(HttpMethod.GET, "/closing/revenue-report", null, logisToken)).isEqualTo(403);

        // 관리자가 조회 권한을 연다
        put("/permissions/screens", Map.of(
                "role", "LOGISTICS", "screenId", closingId, "permission", "READ"));

        // ‼️같은 토큰·같은 요청인데 결과가 달라진다 — 재로그인도, 배포도 없다
        assertThat(status(HttpMethod.GET, "/closing/revenue-report", null, logisToken)).isEqualTo(200);

        // 조회만 열었으니 쓰기는 여전히 막힌다(◐와 ○의 구분)
        assertThat(status(HttpMethod.POST, "/closing/periods/lock",
                Map.of("year", 2055, "month", 1), logisToken)).isEqualTo(403);

        // 되돌린다
        put("/permissions/screens", Map.of(
                "role", "LOGISTICS", "screenId", closingId, "permission", "NONE"));
        assertThat(status(HttpMethod.GET, "/closing/revenue-report", null, logisToken)).isEqualTo(403);
    }

    @Test
    @DisplayName("관리자 권한은 낮출 수 없다 — 스스로를 잠그면 되돌릴 화면이 없다")
    void 관리자_자기잠금_방지() {
        JsonNode r = put("/permissions/screens", Map.of(
                "role", "ADMIN", "screenId", screenId("MASTER"), "permission", "READ"));

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("code").asText()).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("사용자 개별 권한(3단계) — 마감확정·해제를 담당자별로 켜고 끈다")
    void 사용자_개별권한() {
        JsonNode users = data(get("/permissions/users"));
        long logisUserId = 0;
        for (JsonNode u : users) {
            if ("perm-logis".equals(u.path("username").asText())) {
                logisUserId = u.path("userId").asLong();
            }
        }
        assertThat(logisUserId).isNotZero();

        // 켰다 껐다 된다
        put("/permissions/users/" + logisUserId,
                Map.of("flagKey", "PERIOD_LOCK", "granted", true));
        assertThat(flagOf(logisUserId, "periodLock")).isTrue();

        put("/permissions/users/" + logisUserId,
                Map.of("flagKey", "PERIOD_LOCK", "granted", false));
        assertThat(flagOf(logisUserId, "periodLock")).isFalse();

        // 확정과 해제는 따로다 — 확정만 주고 해제는 안 줄 수 있다
        put("/permissions/users/" + logisUserId,
                Map.of("flagKey", "PERIOD_LOCK", "granted", true));
        assertThat(flagOf(logisUserId, "periodLock")).isTrue();
        assertThat(flagOf(logisUserId, "periodUnlock")).isFalse();
    }

    private boolean flagOf(long userId, String field) {
        for (JsonNode u : data(get("/permissions/users"))) {
            if (u.path("userId").asLong() == userId) {
                return u.path(field).asBoolean();
            }
        }
        throw new AssertionError("사용자 없음: " + userId);
    }
}
