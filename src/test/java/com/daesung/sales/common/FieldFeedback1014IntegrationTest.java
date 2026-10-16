package com.daesung.sales.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 대성 현장 피드백 2026-10-14 반영분 — B-1 · B-2 · B-3 · B-4 · B-5 · B-6.
 *
 * <p>실무자가 쓰면서 나온 요구라, <b>화면이 기대하는 모양</b>을 여기서 고정한다.
 * (A-1 미수금현황 기간은 {@code ArStatusPeriodIntegrationTest} 에 따로 있다.)
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("현장 피드백 반영(2026-10-14)")
class FieldFeedback1014IntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-FF" + (System.nanoTime() % 1_000_000L);
    private static final String DATE = "2108-05-20";
    private static final String RANGE = "?fromDate=2108-01-01&toDate=2108-12-31";

    private Long product;
    private Long warehouse;

    @BeforeAll
    void seed() {
        token();
        warehouse = createId("/masters/warehouses",
                Map.of("code", "FFW" + SFX, "name", "현장검증창고", "type", "MAIN"));

        Map<String, Object> b = new HashMap<>();
        b.put("code", "FFP" + SFX);
        b.put("name", "현장검증 교재");
        b.put("contentType", "SELF");
        b.put("price", 10000);
        b.put("supplyRate", 70);
        b.put("catCode", "F2108A01");
        b.put("catName", "현장검증분류");
        product = createId("/masters/products", b);
    }

    @Test
    @DisplayName("★B-1 입고 매입처는 선택 — 비워도 등록된다")
    void 매입처_선택() {
        JsonNode r = post("/stock/inbound", Map.of(
                "processedDate", DATE, "destinationWarehouseId", warehouse,
                "items", List.of(Map.of("productId", product, "unitCost", 1000, "qty", 500))));
        assertThat(r.path("success").asBoolean()).as("매입처 없이 입고: %s", r).isTrue();

        // ‼️다만 **없는 거래처 id 를 보내면 거부**한다 — 조용히 빈칸으로 떨어뜨리면
        //   담당자는 매입처를 골랐다고 믿는데 기록에는 없다.
        JsonNode bad = post("/stock/inbound", Map.of(
                "processedDate", DATE, "supplierClientId", 999_999_999L,
                "destinationWarehouseId", warehouse,
                "items", List.of(Map.of("productId", product, "unitCost", 1000, "qty", 1))));
        assertThat(bad.path("success").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("★★B-3 공급가액을 손으로 넣으면 그 값이 저장된다 + 표시가 붙는다")
    void 금액_직접입력() {
        Long partnerA = partner("AMT");
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product);
        item.put("shipmentType", "NORMAL_SHIP");
        item.put("unitPrice", 10000);
        item.put("supplyRate", 70);
        item.put("qty", 10);
        item.put("supplyAmount", 95_000);   // 계산값 70,000 과 다른 금액

        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", DATE, "partnerId", partnerA, "warehouseId", warehouse,
                "items", List.of(item)));
        assertThat(r.path("success").asBoolean()).as("직접입력: %s", r).isTrue();

        JsonNode row = find(partnerA, "FFP" + SFX);
        assertThat(row.path("supplyAmount").asLong()).as("계산값 70,000 이 아니라 입력값").isEqualTo(95_000);
        assertThat(row.path("amountOverridden").asBoolean()).as("손으로 고친 표시").isTrue();
        // ★정가·공급률은 입력한 그대로 남는다 — 금액에 맞춰 역산하면 담당자가 적은 조건이 사라진다.
        assertThat(row.path("unitPrice").asInt()).isEqualTo(10_000);
        assertThat(row.path("supplyRate").asInt()).isEqualTo(70);

        // 수정에서도 같은 칸이 먹는다.
        long id = row.path("id").asLong();
        assertThat(patch("/sales/" + id, Map.of("supplyAmount", 88_000))
                .path("success").asBoolean()).isTrue();
        assertThat(find(partnerA, "FFP" + SFX).path("supplyAmount").asLong()).isEqualTo(88_000);
    }

    @Test
    @DisplayName("★★B-4 한 번에 올린 줄들이 같은 등록번호 + 1,2,3 순번을 받는다")
    void 등록_묶음과_순번() {
        Long partnerB = partner("ENT");
        JsonNode r = post("/sales/entries", Map.of(
                "salesDate", DATE, "partnerId", partnerB, "warehouseId", warehouse,
                "items", List.of(
                        line(3), line(5), line(7))));
        assertThat(r.path("success").asBoolean()).as("3줄 등록: %s", r).isTrue();

        JsonNode rows = data(get("/sales" + RANGE + "&size=200&partnerIds=" + partnerB))
                .path("content");
        assertThat(rows).hasSize(3);

        String entryNo = rows.get(0).path("entryNo").asText();
        assertThat(entryNo).as("등록번호가 붙는다").startsWith("E-");

        java.util.List<Integer> seqs = new java.util.ArrayList<>();
        for (JsonNode row : rows) {
            assertThat(row.path("entryNo").asText()).as("세 줄이 같은 묶음").isEqualTo(entryNo);
            seqs.add(row.path("entrySeq").asInt());
        }
        assertThat(seqs).as("입력 순서대로 1,2,3").containsExactlyInAnyOrder(1, 2, 3);

        // 다음 등록은 **다른 묶음번호**를 받는다.
        post("/sales/entries", Map.of("salesDate", DATE, "partnerId", partnerB,
                "warehouseId", warehouse, "items", List.of(line(1))));
        JsonNode after = data(get("/sales" + RANGE + "&size=200&partnerIds=" + partnerB))
                .path("content");
        java.util.Set<String> entries = new java.util.HashSet<>();
        after.forEach(x -> entries.add(x.path("entryNo").asText()));
        assertThat(entries).as("등록이 둘이면 묶음도 둘").hasSize(2);
    }

    @Test
    @DisplayName("★★B-2 순매출 거래처별 — 같은 금액 칸, 매입·이익은 비운다")
    void 순매출_거래처별() {
        Long partnerA = partner("NSA");
        Long partnerB = partner("NSB");
        for (Long pt : List.of(partnerA, partnerB)) {
            post("/sales/entries", Map.of("salesDate", DATE, "partnerId", pt,
                    "warehouseId", warehouse, "items", List.of(line(8))));
        }

        JsonNode byProduct = data(get("/sales/net-summary" + RANGE
                + "&partnerIds=" + partnerA + "," + partnerB)).path("rows");
        JsonNode byPartner = data(get("/sales/net-summary" + RANGE
                + "&groupBy=PARTNER&partnerIds=" + partnerA + "," + partnerB)).path("rows");

        assertThat(byProduct).as("도서별 — 상품 하나").hasSize(1);
        assertThat(byPartner).as("거래처별 — 거래처 둘").hasSize(2);

        // 합계는 같아야 한다 — 같은 자료를 다른 축으로 본 것뿐이다.
        long sumProduct = 0;
        for (JsonNode x : byProduct) {
            sumProduct += x.path("saleAmount").asLong();
        }
        long sumPartner = 0;
        for (JsonNode x : byPartner) {
            sumPartner += x.path("saleAmount").asLong();
            assertThat(x.path("partnerCode").asText()).as("거래처 행엔 거래처코드").isNotBlank();
            // ‼️매입단가는 상품 속성이라 거래처로 묶으면 섞인다 — 비워서 틀린 이익을 안 만든다.
            assertThat(x.hasNonNull("purchaseUnitCost")).as("매입단가는 비운다").isFalse();
            assertThat(x.hasNonNull("profit")).as("이익도 비운다").isFalse();
        }
        assertThat(sumPartner).as("축만 다를 뿐 같은 자료다").isEqualTo(sumProduct);
    }

    @Test
    @DisplayName("★★B-5 수금 여러 줄이 한 전표코드로 묶인다")
    void 수금_묶음() {
        Long partnerA = partner("CLA");
        Long partnerB = partner("CLB");
        JsonNode r = post("/closing/collections/batch", Map.of("lines", List.of(
                Map.of("collDate", DATE, "partnerId", partnerA, "collType", "CASH", "collAmt", 300_000),
                Map.of("collDate", DATE, "partnerId", partnerA, "collType", "CASH", "collAmt", 200_000),
                Map.of("collDate", DATE, "partnerId", partnerB, "collType", "CASH", "collAmt", 100_000))));
        assertThat(r.path("success").asBoolean()).as("묶음 등록: %s", r).isTrue();

        JsonNode lines = data(r);
        assertThat(lines).hasSize(3);
        String voucher = lines.get(0).path("voucherNo").asText();
        assertThat(voucher).as("전표코드가 붙는다").startsWith("V-");

        java.util.Set<String> collNos = new java.util.HashSet<>();
        for (JsonNode l : lines) {
            assertThat(l.path("voucherNo").asText()).as("세 줄이 같은 전표").isEqualTo(voucher);
            collNos.add(l.path("collectionNo").asText());
        }
        // ★수금번호는 줄마다 따로다 — 개별 수정·삭제가 그 번호로 된다.
        assertThat(collNos).as("수금번호는 줄마다 다르다").hasSize(3);
    }

    @Test
    @DisplayName("★★B-6 계산서 작성일자를 바꿔 저장한다 — 키는 그대로")
    void 계산서_작성일자_수정() {
        Long partnerA = partner("TAX");
        // 매출 한 건
        Map<String, Object> item = new HashMap<>();
        item.put("productId", product);
        item.put("shipmentType", "NORMAL_SHIP");
        item.put("unitPrice", 10000);
        item.put("supplyRate", 70);
        item.put("qty", 5);
        post("/sales/entries", Map.of("salesDate", DATE, "partnerId", partnerA,
                "warehouseId", warehouse, "items", List.of(item)));

        String to = "2108-05-31";
        JsonNode before = invoice(partnerA, to);
        assertThat(before.path("issueDate").asText()).as("기본은 조회 종료일").isEqualTo(to);

        // 작성일자만 바꾼다. 키(issueDate)는 그대로 보낸다 — 그게 어느 계산서인지 가리킨다.
        JsonNode saved = put("/closing/tax-invoices/adjustments", Map.of(
                "issueDate", to, "partnerId", partnerA, "taxBucket", "FREE",
                "newIssueDate", "2108-05-25", "reason", "실제 발행일에 맞춤"));
        assertThat(saved.path("success").asBoolean()).as("작성일자 보정: %s", saved).isTrue();

        assertThat(invoice(partnerA, to).path("issueDate").asText())
                .as("바뀐 날짜로 나온다").isEqualTo("2108-05-25");
        // ‼️키가 바뀌지 않아야 같은 조회로 계속 찾힌다(바뀌면 보정을 다시 못 건다).
        assertThat(invoice(partnerA, to).path("adjusted").asBoolean()).isTrue();
    }

    /**
     * 테스트마다 **자기 거래처**를 만든다.
     * ‼️공유하면 다른 테스트의 매출이 집계·조회에 섞여 들어온다 —
     *   실제로 이 파일에서 그렇게 깨졌다(B-3 이 B-6 의 매출을 집었다).
     */
    private Long partner(String tag) {
        return createId("/masters/clients",
                Map.of("code", tag + SFX, "name", "현장검증" + tag, "type", "NORMAL"));
    }

    private Map<String, Object> line(int qty) {
        Map<String, Object> m = new HashMap<>();
        m.put("productId", product);
        m.put("shipmentType", "NORMAL_SHIP");
        m.put("unitPrice", 10000);
        m.put("supplyRate", 70);
        m.put("qty", qty);
        return m;
    }

    private JsonNode find(Long partner, String productCode) {
        for (JsonNode r : data(get("/sales" + RANGE + "&size=200&partnerIds=" + partner))
                .path("content")) {
            if (productCode.equals(r.path("productCode").asText())) {
                return r;
            }
        }
        throw new AssertionError("매출 없음");
    }

    private JsonNode invoice(Long partnerA, String to) {
        JsonNode d = data(get("/closing/tax-invoices?fromDate=2108-05-01&toDate=" + to
                + "&partnerId=" + partnerA));
        for (JsonNode inv : d.path("invoices")) {
            if ("FREE".equals(inv.path("taxType").asText())) {
                return inv;
            }
        }
        throw new AssertionError("계산서 없음: " + d);
    }
}
