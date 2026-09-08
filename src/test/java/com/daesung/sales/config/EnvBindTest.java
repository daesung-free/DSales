package com.daesung.sales.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.auth.config.JwtProperties;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.SystemEnvironmentPropertySource;

/**
 * 배포 환경변수 이름이 실제로 바인딩되는지 고정.
 *
 * <p>★<b>이름이 한 글자 틀려도 아무 오류가 안 난다</b> — 기본값으로 조용히 뜬다.
 * https를 붙여 놓고도 쿠키가 Strict로 남으면 "30분 뒤 로그아웃"만 반복하는데,
 * 로그에도 아무것도 안 남아 원인을 찾기 어렵다. deploy.yml이 넘기는 이름 그대로 검증한다.
 *
 * <p>‼️반드시 {@link SystemEnvironmentPropertySource}로 넣어야 한다.
 * {@code withPropertyValues}는 평범한 맵이라 <b>환경변수식 완화 바인딩을 재현하지 못해</b>
 * 실제로는 잘 붙는 이름도 실패로 보인다(이 테스트를 처음 그렇게 썼다가 헛다리를 짚었다).
 */
@DisplayName("배포 환경변수 바인딩")
class EnvBindTest {

    @EnableConfigurationProperties(JwtProperties.class)
    static class Cfg {
    }

    /** deploy.yml이 docker run -e 로 넘기는 것과 같은 모양. */
    private ApplicationContextRunner withEnv(Map<String, Object> env) {
        return new ApplicationContextRunner().withUserConfiguration(Cfg.class)
                .withInitializer(ctx -> ctx.getEnvironment().getPropertySources()
                        .addFirst(new SystemEnvironmentPropertySource("systemEnvironment", env)));
    }

    private static Map<String, Object> env(String... kv) {
        Map<String, Object> m = new HashMap<>();
        m.put("DAESUNG_SECURITY_JWTSECRET", "x".repeat(40));   // deploy.yml이 쓰는 이름 그대로
        for (int i = 0; i < kv.length; i += 2) {
            m.put(kv[i], kv[i + 1]);
        }
        return m;
    }

    @Test
    @DisplayName("★deploy.yml의 세 이름이 전부 먹는다 — JWTSECRET · COOKIESECURE · COOKIESAMESITE")
    void 배포_환경변수가_바인딩된다() {
        withEnv(env("DAESUNG_SECURITY_COOKIESECURE", "true",
                "DAESUNG_SECURITY_COOKIESAMESITE", "None")).run(ctx -> {
            JwtProperties p = ctx.getBean(JwtProperties.class);
            assertThat(p.jwtSecret()).as("시크릿이 안 붙으면 더미 키로 뜬다 = 토큰 위조 가능").hasSize(40);
            assertThat(p.cookieSecure()).as("null이면 이름이 틀린 것(기본값 true로 조용히 통과한다)").isTrue();
            assertThat(p.cookieSameSiteOrDefault()).isEqualTo("None");
            p.assertCookiePolicy();   // https 조합이라 통과해야 한다
        });
    }

    @Test
    @DisplayName("★https 전 기본값(false/Strict)도 정상 조합 — 반쯤 켜진 채로 뜨지 않는다")
    void https_이전_기본값() {
        withEnv(env("DAESUNG_SECURITY_COOKIESECURE", "false",
                "DAESUNG_SECURITY_COOKIESAMESITE", "Strict")).run(ctx -> {
            JwtProperties p = ctx.getBean(JwtProperties.class);
            assertThat(p.cookieSecure()).isFalse();
            p.assertCookiePolicy();
        });
    }

    @Test
    @DisplayName("‼️None인데 Secure를 안 켜면 기동 차단 — 브라우저가 그 쿠키를 버린다")
    void 어긋난_조합은_차단() {
        withEnv(env("DAESUNG_SECURITY_COOKIESECURE", "false",
                "DAESUNG_SECURITY_COOKIESAMESITE", "None")).run(ctx -> {
            JwtProperties p = ctx.getBean(JwtProperties.class);
            org.assertj.core.api.Assertions.assertThatThrownBy(p::assertCookiePolicy)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Secure");
        });
    }
}
