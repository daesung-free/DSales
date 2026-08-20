package com.daesung.sales.receivable;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 수금관리(23p) 구분 2축 회귀 고정.
 *
 * <p>정본 23p 데이터 항목이 <b>수금구분</b>과 <b>입금구분</b>을 나란히 요구한다.
 * 레거시 수금등록.vb:61~62가 그 정체를 알려준다 —
 * {@code collKind as '수금구분'}(무슨 명목) / {@code collType as '입금구분'}(어떤 형태).
 *
 * <p>우리가 갖고 있던 {@code collType}이 입금구분이었고 이름만 '수금유형'으로 잘못 달려 있었다.
 * 두 축을 하나로 합치면 "어음으로 받은 도서대금"을 표현할 수 없다 — 그것을 고정한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("수금관리 구분 2축(23p)")
class CollectionAxisIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-CK" + (System.nanoTime() % 1_000_000L);
    private static final String RANGE = "?fromDate=2035-07-01&toDate=2035-07-31";

    private Long partner;

    @BeforeAll
    void seed() {
        token();
        partner = createId("/masters/clients",
                Map.of("code", "CKP" + SFX, "name", "수금거래처", "type", "NORMAL"));
    }

    @Test
    @DisplayName("두 축이 따로 저장된다 — '어음으로 받은 도서대금'이 표현된다")
    void 두_축_분리() {
        JsonNode d = data(post("/closing/collections", Map.of(
                "collDate", "2035-07-10", "partnerId", partner,
                "collKind", "도서대금", "collType", "PROMISSORY", "collAmt", 500_000,
                "promissoryNo", "가1234", "promissoryDue", "2035-10-31",
                "bankName", "국민은행", "branchName", "강남지점")));

        assertThat(d.path("collKind").asText()).isEqualTo("도서대금");
        assertThat(d.path("collType").asText()).isEqualTo("PROMISSORY");
        assertThat(d.path("collTypeName").asText()).isEqualTo("어음");
        // 어음 정보는 입금구분=어음일 때만 남는다
        assertThat(d.path("promissoryNo").asText()).isEqualTo("가1234");
        assertThat(d.path("bankName").asText()).isEqualTo("국민은행");
    }

    @Test
    @DisplayName("수금구분 미입력이면 '도서대금' — 레거시가 화면에서 고정하던 값")
    void 수금구분_기본값() {
        JsonNode d = data(post("/closing/collections", Map.of(
                "collDate", "2035-07-11", "partnerId", partner,
                "collType", "CASH", "collAmt", 100_000)));

        assertThat(d.path("collKind").asText()).isEqualTo("도서대금");
        assertThat(d.path("collTypeName").asText()).isEqualTo("현금");
    }

    @Test
    @DisplayName("수금구분·입금구분으로 각각 거른다")
    void 두_축_필터() {
        post("/closing/collections", Map.of(
                "collDate", "2035-07-12", "partnerId", partner,
                "collKind", "연체이자", "collType", "CASH", "collAmt", 7_000));

        // 명목으로 거르기
        JsonNode byKind = data(get("/closing/collections" + RANGE
                + "&partnerId=" + partner + "&collKind=연체이자"));
        assertThat(byKind.path("content")).hasSize(1);
        assertThat(byKind.path("content").get(0).path("collAmt").asLong()).isEqualTo(7_000);

        // 형태로 거르기 — 어음 건만
        JsonNode byType = data(get("/closing/collections" + RANGE
                + "&partnerId=" + partner + "&collType=PROMISSORY"));
        assertThat(byType.path("content")).hasSize(1);
        assertThat(byType.path("content").get(0).path("collTypeName").asText()).isEqualTo("어음");

        // 두 축을 같이 걸면 교집합
        JsonNode both = data(get("/closing/collections" + RANGE
                + "&partnerId=" + partner + "&collKind=도서대금&collType=PROMISSORY"));
        assertThat(both.path("content")).hasSize(1);

        // 어긋나는 조합은 0건 — 축이 실제로 둘이라는 증거
        JsonNode none = data(get("/closing/collections" + RANGE
                + "&partnerId=" + partner + "&collKind=연체이자&collType=PROMISSORY"));
        assertThat(none.path("content")).isEmpty();
    }
}
