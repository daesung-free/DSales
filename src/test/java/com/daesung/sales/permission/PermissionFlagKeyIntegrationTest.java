package com.daesung.sales.permission;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 마감 권한 부여 회귀 고정 — 개발팀 점검(2026-09-11) 최우선 건.
 *
 * <p>증상: {@code PUT /permissions/users/1 {"flagKey":"periodLock"}} 이 <b>200을 주는데
 * 저장되지 않았다.</b> 화면은 "권한을 주었습니다"를 띄우고, 다시 조회하면 false다.
 *
 * <p>원인: 서버가 읽는 키는 {@code PERIOD_LOCK}인데 화면은 {@code periodLock}을 보냈고,
 * 저장 쪽이 <b>문자열을 검증 없이 그대로 썼다</b>. 아무도 읽지 않는 행이 생긴 것이다.
 * 조회 응답의 필드명이 {@code periodLock}이라 그 이름으로 되보내는 건 자연스러운 일이었다 —
 * 막았어야 할 쪽은 서버다.
 *
 * <p>★고정하는 것은 두 가지다. <b>두 표기 다 받는다</b>는 것과,
 * <b>모르는 키는 거부한다</b>는 것. 받아 주기만 하면 오타가 났을 때 같은 일이 또 난다.
 *
 * <p>영향이 컸다 — 권한을 아무에게도 줄 수 없어 월마감 화면 전체와
 * 마감 관련 검증 흐름 5개가 통째로 막혔다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("마감 권한 키(2026-09-11 점검 최우선)")
class PermissionFlagKeyIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-PF" + (System.nanoTime() % 1_000_000L);

    private Long userId;

    @BeforeAll
    void seed() {
        token();
        JsonNode r = post("/auth/users", Map.of(
                "username", "pf" + SFX, "password", "Passw0rd!", "name", "권한테스트",
                "role", "FINANCE"));
        assertThat(r.path("success").asBoolean()).as("계정 생성: %s", r).isTrue();
        userId = data(r).path("id").asLong();
    }

    private boolean flag(String field) {
        for (JsonNode u : data(get("/permissions/users"))) {
            if (u.path("userId").asLong() == userId) {
                return u.path(field).asBoolean();
            }
        }
        throw new IllegalStateException("사용자 없음");
    }

    @Test
    @DisplayName("★화면이 보내는 낙타표기(periodLock)로도 실제로 저장된다")
    void 낙타표기() {
        JsonNode r = put("/permissions/users/" + userId,
                Map.of("flagKey", "periodLock", "granted", true));
        assertThat(r.path("success").asBoolean()).as("%s", r).isTrue();

        assertThat(flag("periodLock")).as("★200만 주고 안 저장하던 자리").isTrue();
    }

    @Test
    @DisplayName("대문자 표기(PERIOD_UNLOCK)도 그대로 받는다")
    void 대문자표기() {
        put("/permissions/users/" + userId, Map.of("flagKey", "PERIOD_UNLOCK", "granted", true));
        assertThat(flag("periodUnlock")).isTrue();
    }

    @Test
    @DisplayName("해제도 반영된다 — 켜기만 되고 끄기가 안 되면 권한 회수를 못 한다")
    void 해제() {
        put("/permissions/users/" + userId, Map.of("flagKey", "periodLock", "granted", true));
        put("/permissions/users/" + userId, Map.of("flagKey", "periodLock", "granted", false));
        assertThat(flag("periodLock")).isFalse();
    }

    @Test
    @DisplayName("★모르는 키는 400 — 조용히 통과시키면 이번 일이 그대로 재발한다")
    void 모르는_키는_거부() {
        JsonNode r = put("/permissions/users/" + userId,
                Map.of("flagKey", "periodLockk", "granted", true));

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText())
                .contains("권한 키", "PERIOD_LOCK");
    }
}
