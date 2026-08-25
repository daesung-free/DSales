package com.daesung.sales.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 수기 물류작업비(28p 에디팅) 회귀 고정. 근거: 발주처 회신 2026-08-21 —
 * "행 우클릭으로 물류비를 수기 등록·수정·삭제·복사 … 기존 자동계산 로직에는 영향이 없어야 하며,
 * 수기 등록 건에 대한 소계/누계/합계/총계가 모두 정상 반영".
 *
 * <p>★DSRE 연동이 꺼진 환경에서도 <b>수기 입력은 되어야 한다</b> — DSRE2를 보지 않는 기능이다.
 * 자동계산분과 합쳐진 화면은 연동이 필요해 여기서 검증하지 않는다(복제본 환경에서 확인).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("수기 물류작업비(28p 에디팅)")
class LogisCostManualIntegrationTest extends IntegrationTestSupport {

    /** 다른 테스트의 마감·집계와 겹치지 않는 해. */
    private static final String D = "2048-07-15";

    @BeforeAll
    void auth() {
        token();
    }

    /** Map.of는 10쌍까지라 항목이 더 많은 이 요청은 HashMap으로 만든다. */
    private Map<String, Object> body(long paper, long basic, String memo) {
        Map<String, Object> m = new java.util.HashMap<>();
        m.put("reqDate", D);
        m.put("productCode", "MAN-A");
        m.put("productName", "수기상품");
        m.put("grade", "고3");
        m.put("partnerCode", "C001");
        m.put("partnerName", "수기거래처");
        m.put("paperQty", 10);
        m.put("paperAmount", paper);
        m.put("inwon", 5);
        m.put("basicAmount", basic);
        m.put("tradeAmount", 0);
        m.put("applyGn", "S");
        m.put("memo", memo);
        return m;
    }

    @Test
    @DisplayName("등록 — 금액합계가 자재+기본작업비+출고비로 파생된다")
    void 등록() {
        JsonNode d = data(post("/logistics-costs/manual", body(1000, 500, "야간작업 추가분")));

        assertThat(d.path("paperAmount").asLong()).isEqualTo(1000);
        assertThat(d.path("basicAmount").asLong()).isEqualTo(500);
        assertThat(d.path("totalAmount").asLong()).as("1000 + 500").isEqualTo(1500);
        assertThat(d.path("memo").asText()).isEqualTo("야간작업 추가분");
        // 대응 신청이 없으면 0 — 수기 건은 신청 없이 들어올 수 있다
        assertThat(d.path("reqCd").asInt()).isZero();
    }

    @Test
    @DisplayName("수정 — 귀속 축(접수일자)은 바뀌지 않는다")
    void 수정_귀속축_불변() {
        long id = data(post("/logistics-costs/manual", body(2000, 0, "원본"))).path("id").asLong();

        Map<String, Object> moved = new java.util.HashMap<>(body(3000, 0, "수정본"));
        moved.put("reqDate", "2049-01-01");   // 다른 해로 옮기려는 시도
        JsonNode u = data(put("/logistics-costs/manual/" + id, moved));

        assertThat(u.path("paperAmount").asLong()).as("금액은 바뀐다").isEqualTo(3000);
        assertThat(u.path("reqDate").asText()).as("날짜는 그대로 — 옮기려면 지우고 다시 넣는다")
                .isEqualTo(D);
    }

    @Test
    @DisplayName("복사 — 같은 내용으로 새 행이 생긴다(원본은 남는다)")
    void 복사() {
        long id = data(post("/logistics-costs/manual", body(4000, 0, "복사원본"))).path("id").asLong();

        JsonNode c = data(post("/logistics-costs/manual/" + id + "/copy", Map.of()));
        assertThat(c.path("id").asLong()).isNotEqualTo(id);
        assertThat(c.path("paperAmount").asLong()).isEqualTo(4000);
        assertThat(c.path("memo").asText()).isEqualTo("복사원본");

        // 원본도 그대로 있다
        assertThat(idsIn("2048-07-01", "2048-07-31")).contains(id, c.path("id").asLong());
    }

    @Test
    @DisplayName("삭제 — 목록에서 사라진다(논리삭제)")
    void 삭제() {
        long id = data(post("/logistics-costs/manual", body(5000, 0, "삭제대상"))).path("id").asLong();
        assertThat(idsIn("2048-07-01", "2048-07-31")).contains(id);

        del("/logistics-costs/manual/" + id);
        assertThat(idsIn("2048-07-01", "2048-07-31")).doesNotContain(id);
    }

    @Test
    @DisplayName("마감된 달에는 손대지 못한다 — 막지 않으면 과거 작업비 고정이 뚫린다")
    void 마감월_차단() {
        long id = data(post("/logistics-costs/manual", body(6000, 0, "마감전"))).path("id").asLong();

        post("/closing/periods/lock", Map.of("year", 2048, "month", 7, "memo", "수기 차단 검증"));
        try {
            assertThat(post("/logistics-costs/manual", body(7000, 0, "마감후 등록시도"))
                    .path("error").path("code").asText()).isEqualTo("PERIOD_LOCKED");
            assertThat(put("/logistics-costs/manual/" + id, body(8000, 0, "마감후 수정시도"))
                    .path("error").path("code").asText()).isEqualTo("PERIOD_LOCKED");
            assertThat(del("/logistics-costs/manual/" + id)
                    .path("error").path("code").asText()).isEqualTo("PERIOD_LOCKED");
            assertThat(post("/logistics-costs/manual/" + id + "/copy", Map.of())
                    .path("error").path("code").asText()).isEqualTo("PERIOD_LOCKED");
        } finally {
            post("/closing/periods/unlock", Map.of("year", 2048, "month", 7, "reason", "검증 종료"));
        }

        // 해제하면 다시 손댈 수 있다
        assertThat(put("/logistics-costs/manual/" + id, body(9000, 0, "해제후"))
                .path("success").asBoolean()).isTrue();
    }

    private java.util.List<Long> idsIn(String from, String to) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        data(get("/logistics-costs/manual?fromDate=" + from + "&toDate=" + to))
                .forEach(r -> ids.add(r.path("id").asLong()));
        return ids;
    }
}
