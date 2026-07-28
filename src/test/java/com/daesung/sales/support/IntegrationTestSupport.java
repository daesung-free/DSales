package com.daesung.sales.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * 통합테스트 공용 베이스. 실 MySQL 8 + Redis(Testcontainers) 위에서 앱 전체(HTTP→서비스→쿼리→DB)를 관통.
 * Flyway가 스키마 생성, 인증은 부트스트랩→로그인으로 실제 토큰 사용. DSRE는 off(복제본 불필요).
 * 컨테이너는 static 블록에서 직접 start(싱글톤 패턴) — @DynamicPropertySource 해석 전 기동 보장, JVM 내 재사용.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class IntegrationTestSupport {

    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("sales").withUsername("sales").withPassword("sales");

    static final GenericContainer<?> REDIS = new GenericContainer<>(DockerImageName.parse("redis:7-alpine"))
            .withExposedPorts(6379);

    static {
        MYSQL.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", MYSQL::getJdbcUrl);
        r.add("spring.datasource.username", MYSQL::getUsername);
        r.add("spring.datasource.password", MYSQL::getPassword);
        r.add("spring.data.redis.host", REDIS::getHost);
        r.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        r.add("daesung.dsre.enabled", () -> "false");
        r.add("daesung.supplier.name", () -> "(주)대성테스트");
        r.add("daesung.supplier.biz-no", () -> "111-11-11111");
    }

    @Autowired
    protected TestRestTemplate rest;

    @Autowired
    protected ObjectMapper om;

    private String cachedToken;

    /** 부트스트랩 admin 후 로그인 → access 토큰(클래스 내 캐시). */
    protected String token() {
        if (cachedToken == null) {
            // 최초 관리자 생성(이미 있으면 400이나 무시하고 로그인 진행)
            exchange(HttpMethod.POST, "/auth/bootstrap-admin",
                    Map.of("username", "admin", "password", "Admin1234!", "name", "관리자", "role", "ADMIN"), false);
            JsonNode login = exchange(HttpMethod.POST, "/auth/login",
                    Map.of("username", "admin", "password", "Admin1234!"), false);
            cachedToken = login.path("data").path("accessToken").asText();
        }
        return cachedToken;
    }

    /** 인증 GET → ApiResponse JsonNode 반환. */
    protected JsonNode get(String path) {
        return exchange(HttpMethod.GET, path, null, true);
    }

    /** 인증 POST → ApiResponse JsonNode 반환. */
    protected JsonNode post(String path, Object body) {
        return exchange(HttpMethod.POST, path, body, true);
    }

    /** 인증 PUT → ApiResponse JsonNode 반환. */
    protected JsonNode put(String path, Object body) {
        return exchange(HttpMethod.PUT, path, body, true);
    }

    /** 인증 DELETE → ApiResponse JsonNode 반환. */
    protected JsonNode del(String path) {
        return exchange(HttpMethod.DELETE, path, null, true);
    }

    /** data 노드만 반환(성공 응답 가정). */
    protected JsonNode data(JsonNode apiResponse) {
        return apiResponse.path("data");
    }

    /** POST 후 성공 검증하고 data.id 반환(실패 시 응답 전체를 담아 예외). */
    protected long createId(String path, Object body) {
        JsonNode r = post(path, body);
        if (!r.path("success").asBoolean()) {
            throw new AssertionError("생성 실패 " + path + " → " + r);
        }
        return r.path("data").path("id").asLong();
    }

    /** 헤더 접근이 필요한 원시 호출(Set-Cookie/Cookie 검증용). bearer·cookie는 선택. */
    protected ResponseEntity<String> exchangeRaw(HttpMethod method, String path, Object body,
                                                 String bearer, String cookie) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (bearer != null) {
            h.setBearerAuth(bearer);
        }
        if (cookie != null) {
            h.add(HttpHeaders.COOKIE, cookie);
        }
        String json;
        try {
            json = (body == null) ? null : om.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return rest.exchange("/api/v1" + path, method, new HttpEntity<>(json, h), String.class);
    }

    /** 인증 GET → 바이너리(파일 다운로드) 응답. */
    protected ResponseEntity<byte[]> getBytes(String path) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        return rest.exchange("/api/v1" + path, HttpMethod.GET, new HttpEntity<>(h), byte[].class);
    }

    private JsonNode exchange(HttpMethod method, String path, Object body, boolean auth) {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        if (auth) {
            h.setBearerAuth(token());
        }
        String json;
        try {
            json = (body == null) ? null : om.writeValueAsString(body);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        ResponseEntity<String> resp = rest.exchange(
                "/api/v1" + path, method, new HttpEntity<>(json, h), String.class);
        try {
            return om.readTree(resp.getBody());
        } catch (Exception e) {
            throw new RuntimeException("응답 파싱 실패: " + resp.getBody(), e);
        }
    }
}
