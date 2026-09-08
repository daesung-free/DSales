package com.daesung.sales.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.auth.config.JwtProperties;
import com.daesung.sales.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * CORS 회귀 고정. 프론트(Amplify)와 API가 <b>다른 도메인</b>이라 브라우저가 먼저 preflight를 보낸다.
 *
 * <p>★고정하려는 것은 셋이다.
 * <ol>
 *   <li>Amplify 브랜치 도메인이 허용된다 — <b>브랜치마다 서버를 다시 배포하지 않도록</b> 패턴으로.</li>
 *   <li>자격증명 전송이 허용된다 — refresh가 httpOnly 쿠키라 이게 꺼지면 로그인 유지가 안 된다.</li>
 *   <li>모르는 도메인은 <b>거부</b>된다. 와일드카드를 넓게 열면 남의 사이트가 우리 API를 부른다.</li>
 * </ol>
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("CORS(프론트 별도 도메인)")
class CorsIntegrationTest extends IntegrationTestSupport {

    private static final String AMPLIFY = "https://develop.d1z41nzmh4zrdi.amplifyapp.com";

    @Autowired
    private JwtProperties jwtProperties;

    /** 브라우저가 실제로 보내는 모양의 preflight. */
    private ResponseEntity<String> preflight(String origin) {
        HttpHeaders h = new HttpHeaders();
        h.setOrigin(origin);
        h.setAccessControlRequestMethod(HttpMethod.GET);
        h.setAccessControlRequestHeaders(java.util.List.of("authorization", "content-type"));
        return rest.exchange("/api/v1/masters/products", HttpMethod.OPTIONS,
                new org.springframework.http.HttpEntity<>(h), String.class);
    }

    @Test
    @DisplayName("★Amplify 브랜치 도메인이 허용된다")
    void amplify_허용() {
        ResponseEntity<String> r = preflight(AMPLIFY);

        assertThat(r.getStatusCode().is2xxSuccessful()).as("preflight 응답: %s", r.getStatusCode()).isTrue();
        assertThat(r.getHeaders().getAccessControlAllowOrigin())
                .as("에코된 오리진이 정확히 그 도메인이어야 한다(자격증명 전송이라 '*' 불가)")
                .isEqualTo(AMPLIFY);
    }

    @Test
    @DisplayName("★자격증명 전송 허용 — 꺼지면 refresh 쿠키가 안 실려 로그인 유지가 안 된다")
    void 자격증명_허용() {
        assertThat(preflight(AMPLIFY).getHeaders().getAccessControlAllowCredentials()).isTrue();
    }

    @Test
    @DisplayName("다른 브랜치도 허용된다 — 브랜치마다 서버를 다시 배포하지 않는다")
    void 다른_브랜치도_허용() {
        String other = "https://main.d1z41nzmh4zrdi.amplifyapp.com";
        assertThat(preflight(other).getHeaders().getAccessControlAllowOrigin()).isEqualTo(other);
    }

    @Test
    @DisplayName("★모르는 도메인은 거부 — 와일드카드를 넓게 열면 남의 사이트가 우리 API를 부른다")
    void 모르는_도메인은_거부() {
        // 다른 Amplify 앱(앱 ID가 다르다). *.amplifyapp.com 으로 열어 뒀다면 통과했을 것이다.
        assertThat(preflight("https://develop.dABCDEFGH.amplifyapp.com")
                .getHeaders().getAccessControlAllowOrigin())
                .as("앱 ID를 고정했으므로 남의 amplify 사이트는 막혀야 한다").isNull();
        assertThat(preflight("https://evil.example.com")
                .getHeaders().getAccessControlAllowOrigin()).isNull();
    }

    @Test
    @DisplayName("로컬 개발 서버는 계속 허용 — 프론트 개발이 막히면 안 된다")
    void 로컬은_그대로() {
        assertThat(preflight("http://localhost:5173").getHeaders().getAccessControlAllowOrigin())
                .isEqualTo("http://localhost:5173");
    }

    @Test
    @DisplayName("엑셀 다운로드 파일명을 브라우저가 읽을 수 있다(Content-Disposition 노출)")
    void 파일명_헤더_노출() {
        assertThat(preflight(AMPLIFY).getHeaders().getAccessControlExposeHeaders())
                .contains("Content-Disposition");
    }

    @Test
    @DisplayName("‼️SameSite=None인데 Secure가 꺼져 있으면 기동을 막는다 — 브라우저가 그 쿠키를 버린다")
    void 쿠키정책_불일치는_기동차단() {
        JwtProperties bad = new JwtProperties("x".repeat(40), 30, 14, false, "None");

        org.assertj.core.api.Assertions.assertThatThrownBy(bad::assertCookiePolicy)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Secure");

        // 현재 설정은 정상이어야 한다(로컬은 Strict + http)
        jwtProperties.assertCookiePolicy();
    }
}
