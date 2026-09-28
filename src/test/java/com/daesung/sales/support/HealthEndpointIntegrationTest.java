package com.daesung.sales.support;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * {@code /actuator/health} 가 <b>UP</b> 인지.
 *
 * <p>★<b>이 테스트가 없어서 배포가 막힌 적이 있다</b>(2026-09-28).
 * {@code spring-boot-starter-mail} 을 넣자 액추에이터가 메일 헬스체크를 자동으로 달았고,
 * 기동할 때마다 SMTP 접속을 시도하다 실패해 <b>앱 전체가 DOWN</b> 으로 보고됐다.
 * 배포 스크립트의 기동 검증이 바로 그 값을 보기 때문에 배포가 30회 재시도 끝에 실패했다.
 * 기능은 멀쩡했는데 상태만 DOWN 이었다.
 *
 * <p>★<b>배포 게이트가 보는 값을 테스트도 본다.</b> 의존성을 하나 추가할 때마다
 * 액추에이터가 헬스체크를 하나씩 더 다는데(메일·Redis·DB·디스크…), 그중 하나라도
 * 외부 서비스에 붙으려 하면 여기서 먼저 깨진다. 배포 파이프라인에서 처음 알게 되면
 * 이미 컨테이너가 교체된 뒤다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("기동 상태(actuator/health)")
class HealthEndpointIntegrationTest extends IntegrationTestSupport {

    @Test
    @DisplayName("★health 가 UP 이다 — 배포 기동 검증이 이 값을 본다")
    void 헬스는_UP() {
        // 인증 없이 열려 있어야 한다(배포 스크립트가 토큰 없이 부른다).
        JsonNode body = getRaw("/actuator/health");

        assertThat(body.path("status").asText())
                .as("DOWN 이면 배포가 막힌다. 무엇이 DOWN 인지: %s", body)
                .isEqualTo("UP");
    }

    @Test
    @DisplayName("메일 상태는 앱 상태에 섞이지 않는다 — 메일 서버가 죽어도 배포는 돼야 한다")
    void 메일은_헬스에_없다() {
        JsonNode components = getRaw("/actuator/health").path("components");

        // details 가 안 보이는 설정이면 components 자체가 비어 있다 — 그때는 확인할 것이 없다.
        if (!components.isMissingNode() && components.size() > 0) {
            assertThat(components.has("mail"))
                    .as("메일 헬스체크가 켜져 있으면 SMTP 가 안 될 때 앱이 DOWN 으로 보고된다")
                    .isFalse();
        }
    }
}
