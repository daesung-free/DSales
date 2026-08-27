package com.daesung.sales.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * <b>전 엔드포인트 일괄 호출</b> — OpenAPI 문서에 등록된 모든 오퍼레이션을 한 번씩 눌러
 * <b>500(서버 오류)이 하나도 없는지</b> 본다.
 *
 * <p>★이 테스트가 왜 따로 있나 — 화면별 테스트로는 <b>안 잡히는 결함</b>이 있다.
 * 실제로 이 방식으로 두 건을 잡았다:
 * <ul>
 *   <li>로그아웃이 권한 표에 없어 403 — 매출·재고 테스트는 로그아웃을 부르지 않는다</li>
 *   <li>업로드에 JSON을 보내면 500 — 정상 업로드만 테스트했으니 드러나지 않았다</li>
 * </ul>
 * 개별 화면은 저마다 멀쩡했다. 전부 훑어야 나온다.
 *
 * <p>★판정 기준은 <b>500이 아닐 것</b> 하나다. 400·404·401은 정상 반응이다 —
 * 빈 본문을 보냈으니 검증에 걸리는 게 맞고, 없는 id로 불렀으니 없다고 답하는 게 맞다.
 * 상태코드를 일일이 고정하면 API가 바뀔 때마다 깨져서 아무도 안 보게 된다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("전 엔드포인트 일괄 호출(500 없음)")
class AllEndpointsSweepTest extends IntegrationTestSupport {

    /** 경로 파라미터 자리에 넣는 없는 id. 변경 API가 실제로 뭔가를 고치지 못하게 막는다. */
    private static final String BOGUS = "999999999";

    /**
     * 빈 본문으로도 <b>실제로 일을 하는</b> 변경 API — 일괄 호출에서 뺀다.
     * 부르면 배치가 돌거나 스냅샷·마스터가 바뀌어 다른 테스트를 오염시킨다.
     *
     * <p>‼️조용히 빼지 않는다 — 무엇을 뺐는지 실행할 때마다 출력한다.
     * 목록이 늘어나면 그만큼 이 테스트의 사각지대가 넓어진다는 뜻이라 눈에 보여야 한다.
     * (각 항목은 자기 도메인 테스트에서 따로 검증된다.)
     */
    private static final Set<String> SKIP = new LinkedHashSet<>(List.of(
            "POST /auth/logout",                                  // 토큰을 죽여 뒤 호출이 전부 401
            "POST /batch/jobs/collateral-expiry/run",             // 배치 실행 → 이력행 생성
            "POST /batch/jobs/dashboard-snapshot/run",            // 배치 실행 → 스냅샷 생성
            "POST /closing/carryforward",                         // 이월 스냅샷 재생성
            "POST /masters/schools/sync",                         // DSRE 동기화 → 학교 마스터 갱신
            "POST /sales/bulk/import",                            // 매출 생성(교재)
            "POST /sales/duff/import"));                          // 매출 생성(더프)

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("★모든 오퍼레이션을 한 번씩 호출해 500이 없음을 확인한다")
    void 전수_호출() {
        JsonNode paths = fetchApiDocs().path("paths");
        assertThat(paths.isObject()).as("OpenAPI 문서를 읽지 못했다").isTrue();

        List<String> failures = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        int called = 0;

        var it = paths.fields();
        while (it.hasNext()) {
            var entry = it.next();
            String rawPath = entry.getKey();
            var methods = entry.getValue().fieldNames();
            while (methods.hasNext()) {
                HttpMethod method = HttpMethod.valueOf(methods.next().toUpperCase(java.util.Locale.ROOT));
                String apiPath = rawPath.replaceFirst("^/api/v1", "");
                String key = method.name() + " " + apiPath;
                if (SKIP.contains(key)) {
                    skipped.add(key);
                    continue;
                }

                // 경로 파라미터는 없는 id로 채운다(변경 API가 실제 데이터를 건드리지 못하게).
                String filled = apiPath.replaceAll("\\{[^}]+}", BOGUS);
                // 본문은 비운다 — 검증(400)에 걸리는지 보는 것이지 등록을 시키려는 게 아니다.
                Object body = (method == HttpMethod.POST || method == HttpMethod.PUT) ? Map.of() : null;

                int status;
                try {
                    status = exchangeRaw(method, filled, body, token(), null).getStatusCode().value();
                } catch (Exception e) {
                    failures.add(key + " → 예외 " + e.getClass().getSimpleName() + ": " + e.getMessage());
                    continue;
                }
                called++;
                if (status >= 500) {
                    failures.add(key + " → " + status);
                }
            }
        }

        System.out.println("[전수 호출] 호출 " + called + "건 / 제외 " + skipped.size() + "건");
        skipped.forEach(s -> System.out.println("           제외: " + s));

        assertThat(called).as("호출된 오퍼레이션이 너무 적다 — 문서를 못 읽은 것 아닌지")
                .isGreaterThan(100);
        assertThat(failures).as("500(서버 오류)이 난 엔드포인트").isEmpty();
    }

    /** OpenAPI 문서는 /api/v1 밖에 있어 공용 헬퍼(exchangeRaw)를 쓸 수 없다. */
    private JsonNode fetchApiDocs() {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        ResponseEntity<String> r =
                rest.exchange("/v3/api-docs", HttpMethod.GET, new HttpEntity<>(h), String.class);
        try {
            return om.readTree(r.getBody());
        } catch (Exception e) {
            throw new AssertionError("OpenAPI 문서 파싱 실패: " + r.getStatusCode(), e);
        }
    }
}
