package com.daesung.sales.audit;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;

/**
 * 사용자 행위기록 회귀 고정.
 * 근거: 발주처 요청(2026-09-11) "아이디별로 사용기록이 남게 … <b>직원의 다운로드기록</b>같은 걸
 * 볼 수 있게. 보안상 중요".
 *
 * <p>★고정하려는 것은 <b>무엇을 남기는가</b>와 <b>무엇을 안 남기는가</b> 둘 다다.
 * 조회까지 남기면 하루 수만 건이 쌓여 <b>정작 봐야 할 다운로드 기록이 그 안에 묻힌다.</b>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
// ★순서가 있다 — 앞에서 만든 기록을 뒤에서 조회한다.
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("사용자 행위기록(접근로그)")
class AccessLogIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-AL" + (System.nanoTime() % 1_000_000L);

    @BeforeAll
    void seed() {
        token();
    }

    private JsonNode logs(String query) {
        return data(get("/audit/access-log?size=200" + query)).path("content");
    }

    private boolean hasPath(JsonNode rows, String fragment) {
        for (JsonNode r : rows) {
            if (r.path("path").asText().contains(fragment)) {
                return true;
            }
        }
        return false;
    }

    @Test
    @Order(1)
    @DisplayName("★다운로드가 파일명·크기와 함께 남는다 — 이 표의 존재 이유")
    void 다운로드_기록() {
        // 엑셀을 한 번 받는다
        multipartLessDownload();

        JsonNode rows = logs("&actions=DOWNLOAD");
        assertThat(rows).isNotEmpty();

        JsonNode row = rows.get(0);
        assertThat(row.path("action").asText()).isEqualTo("DOWNLOAD");
        assertThat(row.path("actionName").asText()).isEqualTo("다운로드");
        assertThat(row.path("username").asText()).isEqualTo("admin");
        // ★한글이 그대로 읽혀야 한다. 헤더는 RFC 5987(`filename*=UTF-8''%EA%B1%B0...`)로 나가므로
        //   디코딩을 빠뜨리면 담당자 화면에 %EA%B1%B0... 가 찍힌다 — 무슨 파일인지 알 수 없다.
        assertThat(row.path("fileName").asText()).as("무슨 파일을 받았는지")
                .isNotEmpty()
                .doesNotContain("%")
                .contains("거래처");
        assertThat(row.path("fileSize").asLong()).as("얼마나 가져갔는지").isPositive();
    }

    /** 다운로드 한 번 — 응답 바이트는 버리고 기록만 본다. */
    private void multipartLessDownload() {
        org.springframework.http.HttpHeaders h = new org.springframework.http.HttpHeaders();
        h.setBearerAuth(token());
        rest.exchange("/api/v1/masters/clients/export", org.springframework.http.HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(h), byte[].class);
    }

    @Test
    @Order(2)
    @DisplayName("★등록·수정·삭제가 남는다")
    void 쓰기_기록() {
        long id = createId("/masters/clients",
                Map.of("code", "ALC" + SFX, "name", "로그거래처", "type", "NORMAL"));
        put("/masters/clients/" + id, Map.of("name", "로그거래처2", "type", "NORMAL"));

        assertThat(logs("&actions=CREATE")).isNotEmpty();
        assertThat(logs("&actions=UPDATE")).isNotEmpty();
    }

    @Test
    @Order(3)
    @DisplayName("★조회(GET 목록)는 남기지 않는다 — 남기면 다운로드 기록이 묻힌다")
    void 조회는_안_남는다() {
        // 목록을 여러 번 연다
        for (int i = 0; i < 3; i++) {
            get("/masters/clients?size=5");
        }

        JsonNode all = logs("");
        assertThat(hasPath(all, "/masters/clients?"))
                .as("조회는 기록 대상이 아니다").isFalse();
        // 같은 경로의 '등록'은 남아 있어야 한다(위 테스트에서 만든 것)
        assertThat(all).isNotEmpty();
    }

    @Test
    @Order(4)
    @DisplayName("★토큰 재발급은 남기지 않는다 — 30분마다 자동으로 도는 소음이다")
    void 토큰재발급은_소음() {
        assertThat(hasPath(logs(""), "/auth/refresh")).isFalse();
    }

    @Test
    @Order(5)
    @DisplayName("★실패한 로그인도 남는다 — 막힌 시도가 성공보다 중요할 때가 있다")
    void 실패도_남는다() {
        // 틀린 비밀번호로 시도
        post("/auth/login", Map.of("username", "admin", "password", "wrong-password"));

        JsonNode rows = logs("&actions=LOGIN");
        boolean failed = false;
        for (JsonNode r : rows) {
            if (!r.path("success").asBoolean()) {
                failed = true;
            }
        }
        assertThat(failed).as("실패 로그인이 남아야 한다").isTrue();
    }

    @Test
    @Order(6)
    @DisplayName("‼️조회조건은 남기되 토큰·비밀번호는 가린다")
    void 민감값은_가린다() {
        for (JsonNode r : logs("")) {
            String q = r.path("query").asText("");
            assertThat(q.toLowerCase()).doesNotContain("password=wrong");
        }
    }

    @Test
    @Order(7)
    @DisplayName("사용자·행위·기간으로 좁혀 본다")
    void 필터() {
        assertThat(logs("&username=admin")).isNotEmpty();
        assertThat(logs("&username=없는사용자xyz")).isEmpty();
        assertThat(logs("&actions=DOWNLOAD,DELETE")).isNotEmpty();
    }
}
