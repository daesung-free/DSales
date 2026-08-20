package com.daesung.sales.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 대분류 · 세부구분 축(발주처 회신 2026-08-20) 회귀 고정.
 *
 * <p>지키려는 것 —
 * ① 대분류는 5종 고정이고 IC는 화면에서 빠진다(데이터는 보존).
 * ② 세부구분→대분류 매핑이 회신표 그대로다(D모의고사→기타고사 등).
 * ③ 상품의 대분류는 <b>파생</b>이다 — 매핑을 고치면 상품을 안 건드려도 따라 바뀐다.
 * ④ 마스터에 없는 세부구분은 거부한다(예전 자유 문자열 시절의 오타 유입 차단).
 * ⑤ 쓰는 상품이 있는 세부구분은 지워지지 않고 비활성으로 돌아간다.
 */
class SalesDivisionIntegrationTest extends IntegrationTestSupport {

    /** 실행별 고유 접미어 — 컨테이너를 클래스 간 공유하고 워커 JVM이 재사용돼 코드가 겹치면 깨진다. */
    private final String sfx = String.valueOf(System.nanoTime()).substring(9);

    @Test
    @DisplayName("대분류는 5종 고정이고 IC는 목록에서 빠진다 — 숨김이지 삭제가 아니다")
    void 대분류_5종_고정() {
        JsonNode list = data(get("/masters/sales-divisions/major-categories"));

        // ‼️findValuesAsText는 하위 divisions[].code까지 재귀로 긁는다 — 최상위만 읽어야 한다.
        java.util.List<String> codes = new java.util.ArrayList<>();
        java.util.List<String> names = new java.util.ArrayList<>();
        list.forEach(n -> {
            codes.add(n.path("code").asText());
            names.add(n.path("name").asText());
        });

        assertThat(list).hasSize(5);
        assertThat(codes).containsExactly("MOCK_EXAM", "TEXTBOOK", "ETC_EXAM", "SPECIAL_LECTURE", "ETC");
        assertThat(names).containsExactly("모의고사", "교재", "기타고사", "특강", "기타");
        // IC는 enum에는 살아 있고(과거 데이터 보존) 화면 목록에만 없다.
        assertThat(codes).doesNotContain("IC");
    }

    @Test
    @DisplayName("세부구분→대분류 매핑이 회신표 그대로다")
    void 매핑표_일치() {
        Map<String, String> expected = Map.of(
                "모의고사", "MOCK_EXAM",
                "교재", "TEXTBOOK",
                "D모의고사", "ETC_EXAM",
                "학원콘텐츠", "ETC_EXAM",
                "D:VOCA", "TEXTBOOK",
                "지자체", "SPECIAL_LECTURE",
                "기타", "ETC");

        Map<String, String> actual = new HashMap<>();
        for (JsonNode d : data(get("/masters/sales-divisions"))) {
            actual.put(d.path("code").asText(), d.path("majorCategory").asText());
        }

        assertThat(actual).containsAllEntriesOf(expected);
    }

    @Test
    @DisplayName("상품의 대분류는 세부구분에서 파생된다 — 매핑을 고치면 상품을 안 건드려도 따라 바뀐다")
    void 대분류는_파생값() {
        // 이 테스트 전용 세부구분(교재 소속)
        String code = "테스트구분" + sfx;
        long divisionId = createId("/masters/sales-divisions", Map.of(
                "code", code, "name", code, "majorCategory", "TEXTBOOK", "sortOrder", 50));

        long productId = createId("/masters/products", Map.of(
                "code", "P" + sfx, "name", "파생검증도서" + sfx,
                "contentType", "SELF", "set", false, "price", 10000,
                "taxFree", true, "salesDivision", code));

        JsonNode before = data(get("/masters/products/" + productId));
        assertThat(before.path("majorCategory").asText()).isEqualTo("TEXTBOOK");
        assertThat(before.path("majorCategoryName").asText()).isEqualTo("교재");
        assertThat(before.path("salesDivisionName").asText()).isEqualTo(code);

        // 매핑만 바꾼다(상품은 손대지 않는다)
        put("/masters/sales-divisions/" + divisionId, Map.of(
                "code", code, "name", code, "majorCategory", "SPECIAL_LECTURE"));

        JsonNode after = data(get("/masters/products/" + productId));
        assertThat(after.path("majorCategory").asText()).isEqualTo("SPECIAL_LECTURE");
        assertThat(after.path("majorCategoryName").asText()).isEqualTo("특강");
        // 상품이 들고 있는 값은 여전히 세부구분 코드 하나뿐이다(대분류를 복사해두지 않았다는 증거)
        assertThat(after.path("salesDivision").asText()).isEqualTo(code);
    }

    @Test
    @DisplayName("마스터에 없는 세부구분으로 상품을 등록하면 거부한다")
    void 미등록_세부구분_거부() {
        JsonNode r = post("/masters/products", Map.of(
                "code", "PX" + sfx, "name", "오타도서" + sfx,
                "contentType", "SELF", "set", false, "price", 1000,
                "taxFree", true, "salesDivision", "존재하지않는구분" + sfx));

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("code").asText()).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("세부구분 미지정 상품은 통과하고 대분류는 미분류(null)로 나간다")
    void 미지정은_허용_미분류() {
        long productId = createId("/masters/products", Map.of(
                "code", "PN" + sfx, "name", "미지정도서" + sfx,
                "contentType", "SELF", "set", false, "price", 1000, "taxFree", true));

        // 응답이 null 필드를 생략하므로(NON_NULL) "값이 없다"는 hasNonNull로 본다.
        JsonNode p = data(get("/masters/products/" + productId));
        assertThat(p.hasNonNull("majorCategory")).isFalse();
        assertThat(p.hasNonNull("majorCategoryName")).isFalse();
    }

    @Test
    @DisplayName("쓰는 상품이 있으면 삭제 대신 비활성 — 지우면 과거 매출의 대분류를 잃는다")
    void 사용중이면_비활성() {
        String used = "사용중구분" + sfx;
        long usedId = createId("/masters/sales-divisions", Map.of(
                "code", used, "name", used, "majorCategory", "ETC", "sortOrder", 51));
        createId("/masters/products", Map.of(
                "code", "PU" + sfx, "name", "사용중도서" + sfx,
                "contentType", "SELF", "set", false, "price", 1000,
                "taxFree", true, "salesDivision", used));

        // 사용 중 → false(비활성 처리), 값은 남아 있다
        assertThat(data(del("/masters/sales-divisions/" + usedId)).asBoolean()).isFalse();
        assertThat(codesOf(get("/masters/sales-divisions?includeUnused=true"))).contains(used);
        // 기본 목록(사용중만)에서는 사라진다 — 상품 등록 화면에 다시 뜨면 안 된다
        assertThat(codesOf(get("/masters/sales-divisions"))).doesNotContain(used);

        // 아무도 안 쓰는 구분 → 실제 삭제
        String unused = "미사용구분" + sfx;
        long unusedId = createId("/masters/sales-divisions", Map.of(
                "code", unused, "name", unused, "majorCategory", "ETC", "sortOrder", 52));
        assertThat(data(del("/masters/sales-divisions/" + unusedId)).asBoolean()).isTrue();
        assertThat(codesOf(get("/masters/sales-divisions?includeUnused=true"))).doesNotContain(unused);
    }

    @Test
    @DisplayName("같은 코드로 두 번 등록하면 거부한다")
    void 코드_중복_거부() {
        String code = "중복구분" + sfx;
        Map<String, Object> body = Map.of(
                "code", code, "name", code, "majorCategory", "ETC", "sortOrder", 53);
        createId("/masters/sales-divisions", body);

        assertThat(post("/masters/sales-divisions", body).path("success").asBoolean()).isFalse();
    }

    private java.util.List<String> codesOf(JsonNode apiResponse) {
        return data(apiResponse).findValuesAsText("code");
    }
}
