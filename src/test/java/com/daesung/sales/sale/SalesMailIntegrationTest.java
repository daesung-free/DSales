package com.daesung.sales.sale;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 거래상세내역서 이메일 전송의 <b>안전장치</b>(9/27 회의 A-4, 항목 13·17).
 *
 * <p>★<b>여기서 고정하는 것은 "안 보내는 경우"다.</b> 실제 발송은 SMTP 서버가 있어야 하고
 * 테스트가 진짜 메일을 쏘면 안 된다. 대신 <b>메일이 나가면 안 되는 상황에서 조용히 넘어가지 않는지</b>를 본다 —
 * 조용한 실패가 제일 위험하다. 담당자는 화면에서 오류를 못 보면 보냈다고 믿는다.
 *
 * <p>테스트 환경은 메일 설정이 비어 있어({@code daesung.mail.enabled=false}) 항상 거부 경로다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("거래상세내역서 메일 전송 — 안전장치")
class SalesMailIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("★메일 설정이 없으면 400 — 조용히 실패하면 보냈다고 믿는다")
    void 설정없으면_거부() {
        JsonNode r = post("/sales/email?fromDate=2097-01-01&toDate=2097-12-31", Map.of());

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText())
                .as("무엇을 설정해야 하는지 알려준다")
                .contains("메일");
    }

    @Test
    @DisplayName("기간을 안 주면 400 — 몇 년치가 첨부되는 것을 막는다")
    void 기간없으면_거부() {
        JsonNode r = post("/sales/email", Map.of());

        // 필수 파라미터라 스프링이 먼저 400을 낸다. 어느 쪽이든 200이면 안 된다.
        assertThat(r.path("success").asBoolean()).isFalse();
    }

    @Test
    @DisplayName("외상매출장 쪽도 같은 규칙 — 두 화면이 같은 발송을 쓴다")
    void 외상매출장도_동일() {
        JsonNode r = post("/closing/ar-ledger/email?fromDate=2097-01-01&toDate=2097-12-31", Map.of());

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("message").asText()).contains("메일");
    }
}
