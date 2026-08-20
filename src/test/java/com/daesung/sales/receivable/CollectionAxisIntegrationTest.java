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
    @DisplayName("조회기준 3종 — 기준마다 날짜 필터 대상과 정렬이 함께 바뀐다")
    void 조회기준_3종() {
        // 수금일 7/20, 기장일 8/05 → 두 기준에서 서로 다른 달에 잡힌다
        long id = data(post("/closing/collections", Map.of(
                "collDate", "2035-07-20", "writeDate", "2035-08-05", "partnerId", partner,
                "collType", "CASH", "collAmt", 11_000))).path("id").asLong();

        // 수금일자 기준 7월 조회 → 잡힌다
        assertThat(detailIds(ledger("COLLECT_DATE", "2035-07-01", "2035-07-31"))).contains(id);
        // 기장일자 기준 7월 조회 → 안 잡힌다(기장은 8월)
        assertThat(detailIds(ledger("WRITE_DATE", "2035-07-01", "2035-07-31"))).doesNotContain(id);
        // 기장일자 기준 8월 조회 → 잡힌다
        assertThat(detailIds(ledger("WRITE_DATE", "2035-08-01", "2035-08-31"))).contains(id);
    }

    @Test
    @DisplayName("기장일자가 비어 있으면 기장일자 기준에서 빠진다 — 아직 기표 전이라 그게 맞다")
    void 기장일_미입력() {
        long id = data(post("/closing/collections", Map.of(
                "collDate", "2035-07-21", "partnerId", partner,
                "collType", "CASH", "collAmt", 3_000))).path("id").asLong();

        assertThat(detailIds(ledger("COLLECT_DATE", "2035-07-01", "2035-07-31"))).contains(id);
        assertThat(detailIds(ledger("WRITE_DATE", "2035-07-01", "2035-07-31"))).doesNotContain(id);
    }

    @Test
    @DisplayName("소계: 일 계·월 계·누 계가 붙고 누계가 전체 합계와 같다")
    void 소계() {
        JsonNode l = ledger("COLLECT_DATE", "2035-07-01", "2035-07-31");
        JsonNode rows = l.path("rows");

        java.util.List<String> types = new java.util.ArrayList<>();
        long detailSum = 0;
        for (JsonNode r : rows) {
            types.add(r.path("rowType").asText());
            if ("DETAIL".equals(r.path("rowType").asText())) {
                detailSum += r.path("collAmt").asLong();
            }
        }
        assertThat(types).contains("DETAIL", "DAY_SUBTOTAL", "MONTH_SUBTOTAL", "RUNNING_TOTAL");
        assertThat(types.get(types.size() - 1)).as("마지막은 누 계").isEqualTo("RUNNING_TOTAL");
        assertThat(l.path("total").asLong()).as("누계 = 명세 합계").isEqualTo(detailSum);

        // 일 계 합 = 명세 합 (일계가 빠지거나 겹치면 어긋난다)
        long daySum = 0;
        for (JsonNode r : rows) {
            if ("DAY_SUBTOTAL".equals(r.path("rowType").asText())) {
                daySum += r.path("collAmt").asLong();
            }
        }
        assertThat(daySum).isEqualTo(detailSum);
    }

    @Test
    @DisplayName("수정: 어음에서 현금으로 바꾸면 어음 정보가 지워진다")
    void 수정_어음정보_정리() {
        long id = data(post("/closing/collections", Map.of(
                "collDate", "2035-07-22", "partnerId", partner,
                "collType", "PROMISSORY", "collAmt", 50_000,
                "promissoryNo", "나9999", "promissoryDue", "2035-12-31",
                "bankName", "신한은행", "branchName", "역삼지점"))).path("id").asLong();

        JsonNode u = data(put("/closing/collections/" + id, Map.of(
                "collDate", "2035-07-22", "partnerId", partner,
                "collType", "CASH", "collAmt", 50_000)));

        assertThat(u.path("collTypeName").asText()).isEqualTo("현금");
        assertThat(u.hasNonNull("promissoryNo"))
                .as("현금인데 어음번호가 남으면 유령 어음이 된다").isFalse();
        assertThat(u.hasNonNull("bankName")).isFalse();
    }

    @Test
    @DisplayName("삭제한 수금은 채권 잔액에서 빠진다 — 네이티브 집계까지")
    void 삭제_채권반영() {
        long before = arBalance();

        long id = data(post("/closing/collections", Map.of(
                "collDate", "2035-07-23", "partnerId", partner,
                "collType", "CASH", "collAmt", 40_000))).path("id").asLong();
        assertThat(arBalance()).as("수금하면 잔액이 준다").isEqualTo(before - 40_000);

        del("/closing/collections/" + id);

        // ‼️미수금현황의 수금 합계는 네이티브 쿼리라 @SQLRestriction이 안 걸린다.
        //   조건을 손으로 안 넣으면 지운 수금이 계속 잔액을 깎는다.
        assertThat(arBalance()).as("삭제하면 되돌아온다").isEqualTo(before);
        // 목록에서도 사라진다
        assertThat(detailIds(ledger("COLLECT_DATE", "2035-07-01", "2035-07-31"))).doesNotContain(id);
    }

    /** 이 거래처의 미수금 잔액. */
    private long arBalance() {
        JsonNode rows = data(get("/closing/ar-status?fromDate=2035-01-01&toDate=2035-12-31"
                + "&partnerId=" + partner)).path("rows");
        for (JsonNode r : rows) {
            if (r.path("partnerId").asLong() == partner) {
                return r.path("balance").asLong();
            }
        }
        return 0L;
    }

    private JsonNode ledger(String basis, String from, String to) {
        return data(get("/closing/collections/ledger?basis=" + basis
                + "&fromDate=" + from + "&toDate=" + to + "&partnerId=" + partner));
    }

    private java.util.List<Long> detailIds(JsonNode ledger) {
        java.util.List<Long> ids = new java.util.ArrayList<>();
        for (JsonNode r : ledger.path("rows")) {
            if ("DETAIL".equals(r.path("rowType").asText())) {
                ids.add(r.path("id").asLong());
            }
        }
        return ids;
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
