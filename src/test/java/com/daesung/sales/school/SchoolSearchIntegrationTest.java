package com.daesung.sales.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 학교/학원검색(29p) 회귀 고정.
 * 근거: 레거시 {@code 학교검색.vb} 조회 SQL + 프론트 「백엔드 전달 2026-08-20」 §B-3
 * ("대응 API가 없습니다") + 발주처 화면검토(2026-08-31) 화면명 확정.
 *
 * <p>★핵심은 <b>특약점이 상품군별로 다르다</b>는 것이다 —
 * "같은 학교라도 상품군에 따라 담당 특약점이 다르다".
 * 그래서 특약점명으로 찾을 때 대표·모의고사·IC 세 축을 다 뒤져야 한다.
 * 대표만 보면 모의고사 담당으로 등록된 학교를 못 찾는다.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("학교/학원검색(29p)")
class SchoolSearchIntegrationTest extends IntegrationTestSupport {

    private static final String SFX = "-SC" + (System.nanoTime() % 1_000_000L);

    @BeforeAll
    void seed() {
        token();
        // 대표 특약점은 '가람도서'인데 모의고사는 '나라북스', IC는 '다온에듀'가 맡는 학교
        create("SS1" + SFX, "한빛고등학교", "11", "서울", "강남", "가람도서", "나라북스", "다온에듀");
        // 상품군별 담당이 없는 평범한 학교
        create("SS2" + SFX, "두레중학교", "21", "부산", "해운대", "라온서적", null, null);
    }

    /** Map.of는 10쌍까지라 항목이 더 많은 이 요청은 HashMap으로 만든다. */
    private void create(String code, String name, String cityCode, String city, String loc,
                        String partner, String mock, String ic) {
        Map<String, Object> m = new HashMap<>();
        m.put("schoolCode", code);
        m.put("schoolName", name);
        m.put("custCode", "C" + code);
        m.put("custName", partner);
        m.put("cityCode", cityCode);
        m.put("city", city);
        m.put("partnerLoc", loc);
        m.put("clientCategory", "학교");
        if (mock != null) {
            m.put("mockPartnerName", mock);
        }
        if (ic != null) {
            m.put("icPartnerName", ic);
        }
        JsonNode r = post("/masters/schools", m);
        assertThat(r.path("success").asBoolean()).as("학교 등록: %s", r).isTrue();
    }

    private List<String> codes(String query) {
        List<String> out = new java.util.ArrayList<>();
        data(get("/masters/schools/search" + query)).forEach(n -> out.add(n.path("schoolCode").asText()));
        return out;
    }

    @Test
    @DisplayName("★특약점명 검색이 대표·모의고사·IC 세 축을 다 뒤진다")
    void 특약점_세축_검색() {
        // 대표 특약점으로 찾기
        assertThat(codes("?partnerName=가람도서")).contains("SS1" + SFX);
        // ‼️모의고사 담당으로 찾기 — 대표만 보면 못 찾는 케이스
        assertThat(codes("?partnerName=나라북스"))
                .as("모의고사 담당으로도 찾혀야 한다").contains("SS1" + SFX);
        // IC 담당으로 찾기
        assertThat(codes("?partnerName=다온에듀")).contains("SS1" + SFX);
        // 다른 학교는 안 걸린다
        assertThat(codes("?partnerName=나라북스")).doesNotContain("SS2" + SFX);
    }

    @Test
    @DisplayName("레거시와 같은 컬럼 — 특약점LN은 '소재 + 특약점명'")
    void 컬럼구성() {
        JsonNode row = null;
        for (JsonNode n : data(get("/masters/schools/search?schoolCode=SS1" + SFX))) {
            row = n;
        }
        assertThat(row).isNotNull();
        assertThat(row.path("schoolName").asText()).isEqualTo("한빛고등학교");
        assertThat(row.path("cityCode").asText()).isEqualTo("11");
        assertThat(row.path("cityName").asText()).isEqualTo("서울");
        assertThat(row.path("partnerLoc").asText()).isEqualTo("강남");
        assertThat(row.path("partnerName").asText()).isEqualTo("가람도서");
        assertThat(row.path("partnerLocName").asText()).as("소재 + 특약점명").isEqualTo("강남 가람도서");
        // 레거시는 화면에 안 띄우지만 응답에는 담는다
        assertThat(row.path("mockPartnerName").asText()).isEqualTo("나라북스");
        assertThat(row.path("icPartnerName").asText()).isEqualTo("다온에듀");
    }

    @Test
    @DisplayName("학교명·지역으로도 좁혀진다(부분일치)")
    void 이름_지역_검색() {
        assertThat(codes("?schoolName=한빛")).contains("SS1" + SFX).doesNotContain("SS2" + SFX);
        assertThat(codes("?region=부산")).contains("SS2" + SFX).doesNotContain("SS1" + SFX);
        assertThat(codes("?region=21")).as("지역코드로도").contains("SS2" + SFX);
    }

    @Test
    @DisplayName("★빈 값은 조건에서 빠진다 — NULL인 행이 통째로 사라지면 안 된다")
    void 빈값은_조건에서_제외() {
        // 두레중학교는 모의고사·IC 담당이 없다(null). 특약점명을 안 주면 나와야 한다.
        assertThat(codes("?schoolName=두레")).contains("SS2" + SFX);
        assertThat(codes("?schoolName=두레&partnerName=")).as("빈 문자열도 조건 제외")
                .contains("SS2" + SFX);
    }
}
