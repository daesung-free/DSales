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
import org.springframework.http.HttpStatus;

/**
 * 본인 권한 조회({@code GET /permissions/me}) 회귀 고정.
 *
 * <p>★<b>왜 만들었나.</b> 권한 매트릭스는 관리자 전용이라 역할 계정은 403이고,
 * {@code /auth/me}에는 역할만 있었다. 그래서 프론트가 화면별 권한표를 스스로 들고 있을 수밖에
 * 없었고, <b>관리자가 권한을 바꿔도 역할 계정 메뉴는 그대로</b>였다.
 *
 * <p>고정하려는 것은 넷이다.
 * <ol>
 *   <li>역할 계정도 <b>자기 권한은</b> 읽을 수 있다.</li>
 *   <li>그런데 <b>매트릭스 전체는 여전히 못 읽는다</b> — 열어 준 건 자기 것뿐이다.</li>
 *   <li>관리자가 바꾸면 <b>다음 호출부터 바로</b> 반영된다(배포 불필요).</li>
 *   <li>권한이 NONE인 화면도 <b>목록에 남는다</b> — 빼면 "화면이 없는 것"과 구분이 안 된다.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("본인 권한 조회(/permissions/me)")
class MyPermissionsIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-MP" + (System.nanoTime() % 1_000_000L);

    private String memberToken;

    @BeforeAll
    void seed() {
        token();
        JsonNode created = post("/auth/users", Map.of(
                "username", "mp" + SFX, "password", "Passw0rd!", "name", "권한조회테스트",
                "role", "FINANCE"));
        assertThat(created.path("success").asBoolean()).as("계정 생성: %s", created).isTrue();

        JsonNode login = post("/auth/login", Map.of("username", "mp" + SFX, "password", "Passw0rd!"));
        memberToken = data(login).path("accessToken").asText();
        assertThat(memberToken).isNotBlank();
    }

    /** 역할 계정 토큰으로 호출한다(관리자 토큰이 아니다). */
    private JsonNode asMember(String path) {
        var res = exchangeRaw(HttpMethod.GET, path, null, memberToken, null);
        try {
            return om.readTree(res.getBody());
        } catch (Exception e) {
            throw new IllegalStateException(res.getBody(), e);
        }
    }

    private JsonNode myScreen(String code) {
        for (JsonNode s : data(asMember("/permissions/me")).path("screens")) {
            if (code.equals(s.path("code").asText())) {
                return s;
            }
        }
        return null;
    }

    @Test
    @DisplayName("★역할 계정도 자기 권한은 읽는다 — 여기가 막혀 있어 메뉴가 관리자 설정을 못 따라갔다")
    void 본인_권한_조회() {
        JsonNode d = data(asMember("/permissions/me"));

        assertThat(d.path("role").asText()).isEqualTo("FINANCE");
        assertThat(d.path("screens").size()).as("화면 목록이 비면 메뉴를 그릴 수 없다").isPositive();
        assertThat(d.has("periodLock")).isTrue();
        assertThat(d.has("periodUnlock")).isTrue();

        JsonNode one = d.path("screens").get(0);
        assertThat(one.path("code").asText()).isNotBlank();
        assertThat(one.path("permission").asText()).isIn("NONE", "READ", "WRITE");
        assertThat(one.path("mark").asText()).isIn("–", "◐", "○");
    }

    @Test
    @DisplayName("★매트릭스 전체는 여전히 관리자만 — 열어 준 건 자기 것뿐이다")
    void 매트릭스는_여전히_403() {
        var res = exchangeRaw(HttpMethod.GET, "/permissions/screens", null, memberToken, null);
        assertThat(res.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
    }

    @Test
    @DisplayName("★관리자가 바꾸면 다음 호출부터 바로 반영된다 — 배포 없이 조정한다는 요구의 핵심")
    void 관리자_변경이_즉시_반영() {
        JsonNode screens = data(get("/permissions/screens"));
        JsonNode target = screens.get(0);
        long screenId = target.path("screenId").asLong();
        String code = target.path("code").asText();

        String before = myScreen(code).path("permission").asText();
        String after = "WRITE".equals(before) ? "READ" : "WRITE";

        JsonNode r = put("/permissions/screens",
                Map.of("role", "FINANCE", "screenId", screenId, "permission", after));
        assertThat(r.path("success").asBoolean()).as("%s", r).isTrue();

        assertThat(myScreen(code).path("permission").asText())
                .as("바꾼 값이 바로 보여야 한다").isEqualTo(after);

        // 원래대로 돌려 놓는다 — 다른 테스트가 이 화면 권한에 기대고 있을 수 있다.
        put("/permissions/screens",
                Map.of("role", "FINANCE", "screenId", screenId, "permission", before));
    }

    @Test
    @DisplayName("★권한 NONE인 화면도 목록에 남는다 — 빼면 '화면이 없는 것'과 구분이 안 된다")
    void NONE도_목록에_남는다() {
        JsonNode screens = data(get("/permissions/screens"));
        long screenId = screens.get(0).path("screenId").asLong();
        String code = screens.get(0).path("code").asText();
        String before = myScreen(code).path("permission").asText();

        put("/permissions/screens",
                Map.of("role", "FINANCE", "screenId", screenId, "permission", "NONE"));

        JsonNode s = myScreen(code);
        assertThat(s).as("NONE이라고 목록에서 사라지면 안 된다").isNotNull();
        assertThat(s.path("permission").asText()).isEqualTo("NONE");
        assertThat(s.path("mark").asText()).isEqualTo("–");

        put("/permissions/screens",
                Map.of("role", "FINANCE", "screenId", screenId, "permission", before));
    }
}
