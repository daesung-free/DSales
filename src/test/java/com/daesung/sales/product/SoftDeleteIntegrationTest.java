package com.daesung.sales.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 논리삭제(soft delete) 표준 회귀 고정.
 *
 * <p>게이트규칙 "물리 DELETE 금지"가 실제로 지켜지는지를 DB 행 수준에서 검증한다.
 * 함께 고정하는 것: 삭제행이 유니크 키를 점유해 <b>재등록이 막히지 않는지</b>(V25 del_key 생성컬럼).
 * 이게 깨지면 BOM 재등록이 첫 시도부터 실패한다.
 */
class SoftDeleteIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private JdbcTemplate jdbc;

    private long product(String code, String name) {
        return createId("/masters/products",
                Map.of("code", code, "name", name, "contentType", "SELF", "price", 10000));
    }

    private long countBom(long parentId, boolean deletedOnly) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM bom_items WHERE parent_product_id = ? AND deleted_at IS "
                        + (deletedOnly ? "NOT NULL" : "NULL"), Long.class, parentId);
    }

    @Test
    @DisplayName("BOM 재등록 — 기존 구성은 물리삭제가 아니라 논리삭제로 남고, 조회에선 제외된다")
    void bom_재등록_논리삭제() {
        long set = product("SD-SET1", "논리삭제세트1");
        long a = product("SD-A1", "구성품A1");
        long b = product("SD-B1", "구성품B1");
        long c = product("SD-C1", "구성품C1");

        put("/masters/products/" + set + "/bom", Map.of("components",
                List.of(Map.of("childProductId", a, "ratio", 1), Map.of("childProductId", b, "ratio", 2))));
        assertThat(countBom(set, false)).as("최초 등록 활성행").isEqualTo(2);

        // A+C 로 교체 → B는 빠지고 C가 들어옴
        put("/masters/products/" + set + "/bom", Map.of("components",
                List.of(Map.of("childProductId", a, "ratio", 1), Map.of("childProductId", c, "ratio", 3))));

        JsonNode bom = data(get("/masters/products/" + set + "/bom"));
        List<Long> childIds = bom.path("components").findValuesAsText("childProductId")
                .stream().map(Long::parseLong).toList();
        assertThat(childIds).as("조회에는 현재 구성만 노출").containsExactlyInAnyOrder(a, c);

        assertThat(countBom(set, false)).as("활성행 = 현재 구성 2건").isEqualTo(2);
        assertThat(countBom(set, true)).as("이전 구성 2건이 물리삭제되지 않고 남아야 함").isEqualTo(2);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM bom_items WHERE parent_product_id = ? AND deleted_by = 'admin'",
                Long.class, set)).as("삭제자 기록").isEqualTo(2);
    }

    @Test
    @DisplayName("BOM 동일 구성 재등록 — 삭제행이 유니크 키를 점유하지 않아 같은 구성품을 다시 넣을 수 있다")
    void bom_동일구성_재등록() {
        long set = product("SD-SET2", "논리삭제세트2");
        long a = product("SD-A2", "구성품A2");
        Object body = Map.of("components", List.of(Map.of("childProductId", a, "ratio", 1)));

        put("/masters/products/" + set + "/bom", body);
        put("/masters/products/" + set + "/bom", body);
        JsonNode third = put("/masters/products/" + set + "/bom", body);

        assertThat(third.path("success").asBoolean()).as("동일 구성 반복 등록이 유니크 충돌 없이 성공").isTrue();
        assertThat(countBom(set, false)).as("활성행은 항상 1건").isEqualTo(1);
        assertThat(countBom(set, true)).as("삭제행 2건 누적").isEqualTo(2);
    }

    @Test
    @DisplayName("거래처별 단가 매핑 — 삭제는 논리삭제, 삭제 후 같은 도서×거래처 재등록 가능")
    void 단가매핑_논리삭제_재등록() {
        long book = product("SD-BK", "논리삭제도서");
        long partner = createId("/masters/clients",
                Map.of("code", "SD-CUST", "name", "논리삭제거래처", "type", "NORMAL"));
        String path = "/masters/products/" + book + "/partner-prices/" + partner;

        put(path, Map.of("supplyRate", 70));
        assertThat(data(get(path)).path("supplyRate").asInt()).isEqualTo(70);

        del(path);
        assertThat(get(path).path("success").asBoolean()).as("삭제 후 조회는 404").isFalse();

        Long deleted = jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_partner_price WHERE product_id = ? AND partner_id = ? "
                        + "AND deleted_at IS NOT NULL AND deleted_by = 'admin'", Long.class, book, partner);
        assertThat(deleted).as("행은 남고 삭제자·시각이 기록돼야 함").isEqualTo(1);

        // 같은 조합 재등록 — del_key 없으면 여기서 유니크 충돌
        put(path, Map.of("supplyRate", 65));
        assertThat(data(get(path)).path("supplyRate").asInt()).as("재등록된 공급률").isEqualTo(65);
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM product_partner_price WHERE product_id = ? AND partner_id = ?",
                Long.class, book, partner)).as("삭제행 1 + 활성행 1").isEqualTo(2);
    }

}
