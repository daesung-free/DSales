package com.daesung.sales.sale;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * 통합매출조회 엑셀 다운로드(12p) 회귀 고정.
 * 근거: 개발팀 점검(2026-09-09) P0-7 — "통합 매출 조회는 내려받기 주소 자체가 없습니다.
 * /api/v1/sales/export 는 토큰이 있어도 404 입니다."
 *
 * <p>★조회와 <b>같은 필터</b>를 받아야 한다. 다운로드만 조건이 빠지면 화면에 없던 건이 파일에 실리고,
 * 담당자는 화면을 믿고 그 파일을 보고서에 붙인다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("통합매출조회 엑셀(P0-7)")
class SalesExportIntegrationTest extends IntegrationTestSupport {

    private ResponseEntity<byte[]> download(String query) {
        HttpHeaders h = new HttpHeaders();
        h.setBearerAuth(token());
        return rest.exchange("/api/v1/sales/export" + query, HttpMethod.GET,
                new org.springframework.http.HttpEntity<>(h), byte[].class);
    }

    @Test
    @DisplayName("★404가 아니라 xlsx가 내려온다")
    void 다운로드된다() {
        ResponseEntity<byte[]> r = download("?startDate=2026-01-01&endDate=2026-12-31");

        assertThat(r.getStatusCode().value()).as("이 엔드포인트가 없어 P0-7이 났다").isEqualTo(200);
        assertThat(r.getBody()).isNotNull();
        // xlsx는 zip이다 — 앞 두 바이트가 PK
        assertThat(new String(r.getBody(), 0, 2)).isEqualTo("PK");
    }

    @Test
    @DisplayName("파일명이 헤더에 실린다 — CORS 노출 설정과 짝이다")
    void 파일명() {
        assertThat(download("").getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .contains("attachment");
    }

    @Test
    @DisplayName("조회와 같은 필터를 받는다 — 다중선택 포함")
    void 필터를_받는다() {
        assertThat(download("?tradeClasses=SALES,RETURN&partnerIds=1&includeCanceled=true")
                .getStatusCode().value()).isEqualTo(200);
    }
}
