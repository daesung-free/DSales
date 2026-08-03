package com.daesung.sales.batch;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 배치잡 인프라 + 담보만기 알림 배치 회귀 고정.
 * 근거: 개발문서 19.0(배치잡, 완료조건 "정상완료+실패 재시도 검증") · 25.0(담보만기 1개월전 알림, 25p).
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("배치잡·알림 통합테스트")
class BatchNotificationIntegrationTest extends IntegrationTestSupport {

    /** 담보 만기가 baseDate 기준 daysAhead일 뒤인 거래처 생성. */
    private long partnerWithExpiry(String code, String name, LocalDate expiry) {
        long id = createId("/masters/clients", Map.of("code", code, "name", name, "type", "NORMAL"));
        put("/masters/clients/" + id, Map.of(
                "name", name, "type", "NORMAL",
                "assureAmount", 50_000_000L, "assureExpiry", expiry.toString()));
        return id;
    }

    @Test
    @DisplayName("담보만기 배치 — 만기 1개월 이내 거래처에 알림 적재, 재실행해도 중복 안 쌓임")
    void 담보만기_알림_적재_및_멱등() {
        LocalDate base = LocalDate.of(2026, 3, 10);
        partnerWithExpiry("BT-A", "만기임박거래처", base.plusDays(20));   // 30일 이내 → 대상
        partnerWithExpiry("BT-B", "만기여유거래처", base.plusDays(200));  // 대상 아님

        JsonNode first = data(post("/batch/jobs/collateral-expiry/run?baseDate=" + base, Map.of()));
        assertThat(first.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(first.path("affected").asInt()).as("임박 1건만 적재").isEqualTo(1);

        // 같은 날 재실행 → 새로 쌓이면 안 됨(dedupKey)
        JsonNode second = data(post("/batch/jobs/collateral-expiry/run?baseDate=" + base, Map.of()));
        assertThat(second.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(second.path("affected").asInt()).as("중복 적재 0건").isZero();

        JsonNode list = data(get("/notifications?unreadOnly=true&size=100")).path("content");
        List<String> titles = list.findValuesAsText("title");
        assertThat(titles).anyMatch(t -> t.contains("만기임박거래처"));
        assertThat(titles).noneMatch(t -> t.contains("만기여유거래처"));
        assertThat(titles.stream().filter(t -> t.contains("만기임박거래처")).count())
                .as("같은 거래처 알림은 1건뿐").isEqualTo(1);
    }

    @Test
    @DisplayName("실행 이력 — 시도마다 성공/실패와 처리건수가 남는다")
    void 실행이력_기록() {
        LocalDate base = LocalDate.of(2026, 4, 15);
        post("/batch/jobs/collateral-expiry/run?baseDate=" + base, Map.of());

        JsonNode runs = data(get("/batch/runs?jobName=COLLATERAL_EXPIRY&size=50")).path("content");
        assertThat(runs).isNotEmpty();
        JsonNode row = runs.get(0);
        assertThat(row.path("jobName").asText()).isEqualTo("COLLATERAL_EXPIRY");
        assertThat(row.path("status").asText()).isEqualTo("SUCCESS");
        assertThat(row.path("attempt").asInt()).isGreaterThanOrEqualTo(1);
        assertThat(row.path("startedAt").isMissingNode()).isFalse();
        assertThat(row.path("finishedAt").isMissingNode()).isFalse();
    }

    @Test
    @DisplayName("알림 확인 처리 — 읽으면 미확인 목록에서 빠진다(팝업 재노출 방지)")
    void 알림_읽음처리() {
        LocalDate base = LocalDate.of(2026, 5, 20);
        partnerWithExpiry("BT-C", "읽음처리거래처", base.plusDays(10));
        post("/batch/jobs/collateral-expiry/run?baseDate=" + base, Map.of());

        JsonNode unread = data(get("/notifications?unreadOnly=true&size=100")).path("content");
        JsonNode target = null;
        for (JsonNode n : unread) {
            if (n.path("title").asText().contains("읽음처리거래처")) {
                target = n;
            }
        }
        assertThat(target).as("미확인 알림에 있어야 함").isNotNull();

        JsonNode read = data(put("/notifications/" + target.path("id").asLong() + "/read", Map.of()));
        assertThat(read.path("readYn").asBoolean()).isTrue();

        JsonNode after = data(get("/notifications?unreadOnly=true&size=100")).path("content");
        assertThat(after.findValuesAsText("title"))
                .as("읽은 알림은 미확인 목록에서 제외").noneMatch(t -> t.contains("읽음처리거래처"));
    }
}
