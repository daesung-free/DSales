package com.daesung.sales.common;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

/**
 * 전 API 공통 계약 회귀 고정. 근거: 전 엔드포인트 점검(168개 오퍼레이션 일괄 호출)에서
 * 잡힌 결함 2건을 다시 나지 않게 박아 둔다.
 *
 * <p>둘 다 <b>개별 화면 테스트로는 안 잡히는</b> 종류다 — 화면 하나만 보면 정상이고,
 * 전체를 훑어야 드러난다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("전 API 공통 계약")
class ApiContractIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("★로그인한 사용자는 로그아웃할 수 있다 — 권한 표에 없다고 막히면 안 된다")
    void 로그아웃() {
        // 경로별 권한을 표(V52)로 옮기면서 /auth/** 가 표에 없어,
        // 쓰기(POST)인 로그아웃이 deny-by-default에 걸려 403이 났었다.
        // 로그아웃은 역할로 통제할 대상이 아니다 — 인증만 되면 항상 가능해야 한다.
        assertThat(exchangeRaw(HttpMethod.POST, "/auth/logout", null, token(), null)
                .getStatusCode().value()).isEqualTo(200);
    }

    @Test
    @DisplayName("★업로드 API에 JSON을 보내면 415다 — 500(서버 오류)이 아니다")
    void 잘못된_컨텐트타입() {
        // 파일 업로드는 multipart만 받는데, JSON으로 부르면 마지막 Exception 핸들러까지
        // 흘러 500 + ERROR 로그가 났다. 클라이언트 실수가 서버 장애로 보인다.
        // (같은 부류인 405 '메서드 불일치'는 이미 처리돼 있었고 415만 빠져 있었다.)
        for (String path : new String[] {"/sales/upload", "/logistics/work-orders/tracking/upload"}) {
            ResponseEntity<String> r = exchangeRaw(HttpMethod.POST, path, Map.of(), token(), null);
            assertThat(r.getStatusCode().value()).as("%s", path).isEqualTo(415);
            assertThat(r.getBody()).as("어떤 타입을 받는지 알려준다 — 415만 던지면 프론트가 못 고친다")
                    .contains("multipart");
        }
    }

    @Test
    @DisplayName("경로는 맞고 메서드만 틀리면 405")
    void 메서드_불일치() {
        assertThat(exchangeRaw(HttpMethod.DELETE, "/sales/summary", null, token(), null)
                .getStatusCode().value()).isEqualTo(405);
    }

    @Test
    @DisplayName("정상 multipart 업로드는 415가 아니다 — 415 처리가 정상 경로를 막지 않는지")
    void 정상_multipart는_통과() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.MULTIPART_FORM_DATA);
        h.setBearerAuth(token());
        org.springframework.util.MultiValueMap<String, Object> body =
                new org.springframework.util.LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource("not-an-xlsx".getBytes()) {
            @Override
            public String getFilename() {
                return "x.xlsx";
            }
        });
        ResponseEntity<String> r = rest.exchange("/api/v1/sales/upload", HttpMethod.POST,
                new HttpEntity<>(body, h), String.class);
        // 내용이 엑셀이 아니라 400이 맞다. 415가 나오면 multipart 자체를 못 받는 것이다.
        assertThat(r.getStatusCode().value()).as("응답: %s", r.getBody()).isNotEqualTo(415);
    }
}
