package com.daesung.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * API 계약(OpenAPI 문서) 회귀 고정.
 *
 * <p>여기서 막는 두 결함은 <b>앱이 정상 기동하고 모든 기능테스트가 통과하는 상태에서도 성립</b>한다.
 * 서버 응답은 멀쩡한데 프론트가 보는 문서만 틀리기 때문에, 연동 단계에 가서야 드러난다.
 * 실제로 2026-08-06 서버 검증에서 둘 다 발견됐다.
 */
@DisplayName("API 계약 통합테스트")
class ApiContractIntegrationTest extends IntegrationTestSupport {

    /** springdoc 문서는 /api/v1 prefix 밖에 있어 베이스 헬퍼(get)를 쓰지 않는다. */
    private JsonNode apiDocs() {
        try {
            String body = rest.getForObject("/v3/api-docs", String.class);
            return om.readTree(body);
        } catch (Exception e) {
            throw new IllegalStateException("OpenAPI 문서를 읽지 못했습니다.", e);
        }
    }

    @Test
    @DisplayName("중첩 record가 같은 이름으로 뭉개지지 않는다 — 요청 본문 문서가 실제와 달라지는 것을 막는다")
    void 스키마_이름충돌_없음() {
        JsonNode schemas = apiDocs().path("components").path("schemas");

        // 중첩 record를 그냥 두면 springdoc이 홑이름(Item/Row/Line…)으로 등록해
        // 서로 다른 DTO 수십 개가 스키마 하나로 덮어써진다. 마지막에 등록된 것만 살아남아
        // 나머지 엔드포인트의 요청/응답 문서가 전부 엉뚱한 필드를 가리키게 된다.
        List<String> collapsed = new ArrayList<>();
        for (String bare : List.of("Item", "Row", "Line", "Component", "Summary", "Detail", "Entry")) {
            if (schemas.has(bare)) {
                collapsed.add(bare);
            }
        }
        assertThat(collapsed)
                .as("홑이름 스키마가 있으면 중첩 record 충돌이다. 해당 record에 @Schema(name=\"바깥이름+안이름\")을 붙일 것")
                .isEmpty();

        // 대표 케이스: 매출등록 품목. 한때 이동(Transfer) 품목으로 덮어써져
        // 필수값 shipmentType이 문서에서 사라진 적이 있다.
        JsonNode item = schemas.path("SalesEntryItem").path("properties");
        assertThat(item.fieldNames()).toIterable()
                .as("매출등록 품목 문서")
                .contains("productId", "shipmentType", "qty", "unitPrice", "supplyRate", "tax");
    }

    @Test
    @DisplayName("기간 조회 파라미터 이름이 전 엔드포인트에서 fromDate/toDate로 통일돼 있다")
    void 날짜_파라미터_이름_통일() {
        JsonNode paths = apiDocs().path("paths");

        // 같은 '기간 조회'인데 화면마다 from/to와 fromDate/toDate로 갈리면
        // 프론트가 엔드포인트마다 규칙을 외워야 하고, 틀리면 400이 아니라
        // (파라미터가 선택값인 곳에서는) 조용히 전체기간이 조회된다.
        Map<String, List<String>> legacy = new TreeMap<>();
        for (Iterator<String> it = paths.fieldNames(); it.hasNext(); ) {
            String path = it.next();
            JsonNode ops = paths.path(path);
            for (Iterator<String> mi = ops.fieldNames(); mi.hasNext(); ) {
                String method = mi.next();
                List<String> names = new ArrayList<>();
                for (JsonNode p : ops.path(method).path("parameters")) {
                    if ("query".equals(p.path("in").asText())) {
                        names.add(p.path("name").asText());
                    }
                }
                if (names.contains("from") || names.contains("to")) {
                    legacy.computeIfAbsent(path, k -> new ArrayList<>()).add(method);
                }
            }
        }
        assertThat(legacy)
                .as("from/to를 쓰는 엔드포인트가 남아 있다 — @RequestParam(name=\"fromDate\")로 통일할 것")
                .isEmpty();
    }
}
