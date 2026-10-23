package com.daesung.sales.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 학교/학원검색이 거래처를 <b>DSRE 코드</b>로 찾는지.
 *
 * <p>근거: 프론트 실측 2026-10-23 §6③ — "학교검색: 거래처명1(partnerName1)·지역명(regionName)이
 * 비어 옵니다. 조인 확인 부탁드립니다."
 *
 * <h3>무엇이 틀렸나</h3>
 * 코드 체계가 둘인데 하나로만 찾고 있었다.
 * <pre>
 *   학교  schools.cust_code = '10003'      ← DSRE 거래처코드(이관 4,857/4,860 이 숫자)
 *   거래처 partners.code     = 'A10A195'   ← DSLab 매출코드
 * </pre>
 * {@code cust_code = code} 로 찾으니 <b>한 건도 안 붙었다.</b>
 *
 * <p>‼️{@code dsreCode} 는 <b>DSRE 동기화만 쓰는 칸</b>이라 등록 API에 입력이 없다.
 * 그리고 동기화는 DSRE 연동이 켜져야 도는데 CI는 끄고 돈다 —
 * 그래서 이 테스트는 리포지토리로 값을 직접 심고 <b>조인만</b> 본다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("학교검색 ↔ 거래처 조인(DSRE 코드)")
class SchoolPartnerJoinIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-SPJ" + (System.nanoTime() % 1_000_000L);
    private static final String DSRE_CODE = "9" + (System.nanoTime() % 90_000L + 10_000L);

    @Autowired
    private PartnerRepository partnerRepository;

    private String schoolByDsre;
    private String schoolByOurCode;
    private String ourCode;

    @BeforeAll
    void seed() {
        token();
        ourCode = "SPJP" + SFX;
        Long partnerId = createId("/masters/clients", Map.of(
                "code", ourCode, "name", "조인검증거래처(풀네임)", "name1", "조인검증거래처",
                "type", "NORMAL", "region", "강남관할"));

        // DSRE 동기화가 하는 일을 흉내 낸다 — 등록 API엔 이 칸이 없다.
        // ‼️saveAndFlush 로 **명시 저장**한다. 여기는 트랜잭션 밖이라 조회한 엔티티가
        //   detached 다 — 값만 바꾸고 두면 변경감지가 안 돌아 UPDATE 가 안 나간다
        //   (처음에 그렇게 짰다가 DSRE 경로만 빈 칸으로 떨어졌다).
        var p = partnerRepository.findById(partnerId).orElseThrow();
        p.applyDsreCode(DSRE_CODE);
        partnerRepository.saveAndFlush(p);

        // ① 이관된 학교의 모양 — 거래처코드 칸에 **DSRE 코드**가 들어 있다.
        schoolByDsre = "SPJS1" + SFX;
        createSchool(schoolByDsre, DSRE_CODE);

        // ② 수기로 등록한 학교 — 우리 거래처코드를 적었을 수 있다(폴백 경로).
        schoolByOurCode = "SPJS2" + SFX;
        createSchool(schoolByOurCode, ourCode);
    }

    private void createSchool(String code, String custCode) {
        Map<String, Object> s = new HashMap<>();
        s.put("schoolCode", code);
        s.put("schoolName", "조인검증학교" + code);
        s.put("custCode", custCode);
        s.put("custName", "조인검증거래처");
        s.put("city", "서울");
        JsonNode r = post("/masters/schools", s);
        assertThat(r.path("success").asBoolean()).as("학교 등록: %s", r).isTrue();
    }

    private JsonNode row(String schoolCode) {
        JsonNode rows = data(get("/masters/schools/search?schoolCode=" + schoolCode)).path("content");
        assertThat(rows).as("학교가 조회돼야 한다: %s", schoolCode).isNotEmpty();
        return rows.get(0);
    }

    @Test
    @DisplayName("★DSRE 코드로 붙는다 — 이관 학교가 들고 있는 코드가 그것이다")
    void DSRE코드로_붙는다() {
        JsonNode r = row(schoolByDsre);

        // ‼️예전엔 둘 다 빈 칸이었다. 코드 체계가 달라 거래처를 아예 못 찾았다.
        assertThat(r.path("partnerName1").asText()).as("거래처명1(상호)").isEqualTo("조인검증거래처");
        assertThat(r.path("partnerName2").asText()).as("거래처명2(풀네임)")
                .isEqualTo("조인검증거래처(풀네임)");

        // ★지역명은 학교에 없으면 거래처 지역으로 채운다 —
        //   schools.region 은 이관에서 아예 안 들어왔다(전 행 NULL).
        assertThat(r.path("regionName").asText()).as("거래처 지역으로 폴백").isEqualTo("강남관할");
    }

    @Test
    @DisplayName("우리 거래처코드로 적힌 학교도 그대로 붙는다 — 수기 등록 경로")
    void 우리코드로도_붙는다() {
        JsonNode r = row(schoolByOurCode);
        assertThat(r.path("partnerName1").asText()).isEqualTo("조인검증거래처");
        assertThat(r.path("regionName").asText()).isEqualTo("강남관할");
    }

    @Test
    @DisplayName("‼️두 코드는 다른 값이다 — 같으면 이 테스트가 아무것도 증명하지 못한다")
    void 두_코드가_다르다() {
        assertThat(DSRE_CODE).isNotEqualTo(ourCode);
        assertThat(DSRE_CODE).as("DSRE 코드는 숫자 체계").matches("\\d+");
        assertThat(ourCode).as("우리 코드는 영문 접두").matches("[A-Za-z].*");
    }
}
