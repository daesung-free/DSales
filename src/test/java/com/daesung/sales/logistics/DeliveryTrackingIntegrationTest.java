package com.daesung.sales.logistics;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 작업요청서(26p) 발송구분·수령인·송장 회귀 고정.
 *
 * <p>근거: 정본 26p [업데이트 2026-07-25 ★클라이언트 정정★] —
 * "작업 대기 리스트의 '상태변경' 옆에 '발송구분(택배/화물)' 필드 추가.
 * '택배' 선택 시 담당자 정보가 노출".
 *
 * <p>프론트는 이미 `발송구분`·`수령인` 컬럼을 그리고 있었는데 우리가 값을 주지 않았다.
 * 그 계약을 고정한다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("작업요청서 발송구분·송장(26p)")
class DeliveryTrackingIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-DT" + (System.nanoTime() % 1_000_000L);
    private static final String RANGE = "?fromDate=2033-05-01&toDate=2033-05-31";

    private long courierShipment;
    private long freightShipment;

    @BeforeAll
    void seed() {
        token();
        Long sup = createId("/masters/clients", Map.of("code", "DTS" + SFX, "name", "인쇄", "type", "NORMAL"));
        Long partner = createId("/masters/clients",
                Map.of("code", "DTP" + SFX, "name", "발송거래처", "type", "NORMAL"));
        Long wh = createId("/masters/warehouses",
                Map.of("code", "DTW" + SFX, "name", "물류", "type", "MAIN"));
        Long book = createId("/masters/products", Map.of("code", "DTA" + SFX, "name", "교재도서",
                "contentType", "SELF", "price", 10000, "supplyRate", 75, "salesDivision", "교재"));
        Long mock = createId("/masters/products", Map.of("code", "DTB" + SFX, "name", "모의도서",
                "contentType", "SELF", "price", 5000, "supplyRate", 75, "salesDivision", "모의고사"));
        post("/stock/inbound", Map.of("processedDate", "2033-05-01", "supplierClientId", sup,
                "destinationWarehouseId", wh,
                "items", List.of(Map.of("productId", book, "unitCost", 3000, "qty", 500),
                        Map.of("productId", mock, "unitCost", 1000, "qty", 500))));

        // 분류가 다르면 발송 건이 둘로 나뉜다 → 하나는 택배, 하나는 화물로 쓴다
        post("/sales/entries", Map.of("salesDate", "2033-05-10", "partnerId", partner, "warehouseId", wh,
                "items", List.of(
                        Map.of("productId", book, "shipmentType", "NORMAL_SHIP", "qty", 10,
                                "schoolCode", "20001", "schoolName", "발송고등학교"),
                        Map.of("productId", mock, "shipmentType", "NORMAL_SHIP", "qty", 5,
                                "schoolCode", "20001", "schoolName", "발송고등학교"))));

        JsonNode orders = data(get("/logistics/work-orders" + RANGE));
        assertThat(orders).hasSize(2);
        courierShipment = orders.get(0).path("id").asLong();
        freightShipment = orders.get(1).path("id").asLong();
    }

    @Test
    @DisplayName("발송구분·수령인이 저장되고 코드와 한글명이 함께 나온다")
    void 발송구분_수령인() {
        put("/logistics/work-orders/" + courierShipment + "/shipping", Map.of(
                "deliveryType", "COURIER", "receiverName", "홍길동",
                "receiverPhone", "010-1234-5678", "boxCount", 3));

        JsonNode o = orderById(courierShipment);
        assertThat(o.path("deliveryType").asText()).isEqualTo("COURIER");
        assertThat(o.path("deliveryTypeName").asText()).isEqualTo("택배");
        assertThat(o.path("receiverName").asText()).isEqualTo("홍길동");
        assertThat(o.path("receiverPhone").asText()).isEqualTo("010-1234-5678");
        assertThat(o.path("boxCount").asInt()).isEqualTo(3);
    }

    @Test
    @DisplayName("기본값이 없다 — 고르지 않은 건은 미지정으로 남는다")
    void 기본값_없음() {
        // 발송구분을 한 번도 안 준 건
        JsonNode o = orderById(freightShipment);
        assertThat(o.hasNonNull("deliveryType"))
                .as("정본이 자동 기본값 여부를 미해결로 남겨, 임의로 채우지 않는다").isFalse();
    }

    @Test
    @DisplayName("보내지 않은 항목은 건드리지 않는다 — 박스 수만 고쳐도 수령인이 남는다")
    void 부분수정_보존() {
        put("/logistics/work-orders/" + courierShipment + "/shipping", Map.of(
                "deliveryType", "COURIER", "receiverName", "김수령", "boxCount", 1));
        put("/logistics/work-orders/" + courierShipment + "/shipping", Map.of("boxCount", 9));

        JsonNode o = orderById(courierShipment);
        assertThat(o.path("boxCount").asInt()).isEqualTo(9);
        assertThat(o.path("receiverName").asText()).as("수령인이 지워지면 택배가 누구에게 가는지 모른다")
                .isEqualTo("김수령");
        assertThat(o.path("deliveryTypeName").asText()).isEqualTo("택배");
    }

    @Test
    @DisplayName("발송구분으로 목록을 거른다")
    void 발송구분_필터() {
        put("/logistics/work-orders/" + courierShipment + "/shipping", Map.of("deliveryType", "COURIER"));
        put("/logistics/work-orders/" + freightShipment + "/shipping", Map.of("deliveryType", "FREIGHT"));

        JsonNode courier = data(get("/logistics/work-orders" + RANGE + "&deliveryType=COURIER"));
        assertThat(courier).hasSize(1);
        assertThat(courier.get(0).path("id").asLong()).isEqualTo(courierShipment);

        JsonNode freight = data(get("/logistics/work-orders" + RANGE + "&deliveryType=FREIGHT"));
        assertThat(freight).hasSize(1);
        assertThat(freight.get(0).path("id").asLong()).isEqualTo(freightShipment);
    }

    @Test
    @DisplayName("송장 일괄 등록: 아는 행만 반영하고 모르는 행은 사유와 함께 돌려준다")
    void 송장_일괄등록() throws Exception {
        byte[] xlsx = trackingFile(new Object[][]{
                {courierShipment, "CJ대한통운", "123456789012"},
                {999_999_999L, "CJ대한통운", "999"},          // 없는 발송 건
                {freightShipment, "대신화물", ""},             // 송장번호 비어 있음
        });

        JsonNode r = data(uploadTracking(xlsx));
        assertThat(r.path("updated").asInt()).isEqualTo(1);
        assertThat(r.path("failed").asInt()).isEqualTo(2);

        // 반영된 건은 조회에 나온다
        JsonNode o = orderById(courierShipment);
        assertThat(o.path("trackingNo").asText()).isEqualTo("123456789012");
        assertThat(o.path("courierName").asText()).isEqualTo("CJ대한통운");

        // 실패 행은 사유가 붙는다 — 건수만 주면 어느 줄이 문제인지 모른다
        JsonNode lines = r.path("lines");
        boolean hasReason = false;
        for (JsonNode l : lines) {
            if ("ERROR".equals(l.path("status").asText())) {
                assertThat(l.path("message").asText()).isNotBlank();
                hasReason = true;
            }
        }
        assertThat(hasReason).isTrue();
    }

    @Test
    @DisplayName("송장 양식에 대조 키(발송건ID)가 들어 있다")
    void 송장양식_대조키() throws Exception {
        byte[] xlsx = getBytes("/logistics/work-orders/tracking/template"
                + "?fromDate=2033-05-01&toDate=2033-05-31").getBody();
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook(
                new java.io.ByteArrayInputStream(xlsx))) {
            var sheet = wb.getSheetAt(0);
            var header = sheet.getRow(0);
            assertThat(header.getCell(0).getStringCellValue()).isEqualTo("발송건ID");
            assertThat(header.getCell(2).getStringCellValue()).isEqualTo("송장번호");
            // 담당자가 ID를 따로 채울 필요가 없도록 값이 이미 들어 있어야 한다
            assertThat(sheet.getRow(1).getCell(0).getNumericCellValue()).isGreaterThan(0);
        }
    }

    /** 3컬럼(발송건ID·택배사·송장번호) xlsx 생성. */
    private byte[] trackingFile(Object[][] rows) throws Exception {
        try (var wb = new org.apache.poi.xssf.usermodel.XSSFWorkbook();
             var out = new java.io.ByteArrayOutputStream()) {
            var sheet = wb.createSheet("송장");
            var head = sheet.createRow(0);
            head.createCell(0).setCellValue("발송건ID");
            head.createCell(1).setCellValue("택배사");
            head.createCell(2).setCellValue("송장번호");
            for (int i = 0; i < rows.length; i++) {
                var r = sheet.createRow(i + 1);
                r.createCell(0).setCellValue(((Number) rows[i][0]).doubleValue());
                r.createCell(1).setCellValue((String) rows[i][1]);
                r.createCell(2).setCellValue((String) rows[i][2]);
            }
            wb.write(out);
            return out.toByteArray();
        }
    }

    private JsonNode uploadTracking(byte[] xlsx) throws Exception {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setBearerAuth(token());
        headers.setContentType(org.springframework.http.MediaType.MULTIPART_FORM_DATA);
        var body = new org.springframework.util.LinkedMultiValueMap<String, Object>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(xlsx) {
            @Override
            public String getFilename() {
                return "tracking.xlsx";
            }
        });
        var resp = rest.exchange("/api/v1/logistics/work-orders/tracking/upload",
                org.springframework.http.HttpMethod.POST,
                new org.springframework.http.HttpEntity<>(body, headers), String.class);
        return om.readTree(resp.getBody());
    }

    private JsonNode orderById(long id) {
        for (JsonNode o : data(get("/logistics/work-orders" + RANGE))) {
            if (o.path("id").asLong() == id) {
                return o;
            }
        }
        throw new AssertionError("발송 건을 찾지 못함: " + id);
    }
}
