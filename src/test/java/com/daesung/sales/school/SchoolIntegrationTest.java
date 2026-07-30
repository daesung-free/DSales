package com.daesung.sales.school;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/** 학교관리 마스터(35p) 통합테스트 — 등록·조회·수정·목록검색·엑셀. */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("학교관리 마스터 통합테스트")
class SchoolIntegrationTest extends IntegrationTestSupport {

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("학교 등록·조회·수정 — 35p 컬럼(거래처코드·도시·지역·학교Y/N·학교학원구분)")
    void 학교_CRUD() {
        long id = createId("/masters/schools", Map.of(
                "schoolCode", "SCH-001", "custCode", "SCH-001", "custName", "진주 이룸도서",
                "city", "진주", "region", "경남", "schoolName", "진주고등학교",
                "isSchool", true, "schoolType", "SCHOOL", "clientCategory", "특약점", "memo", "테스트"));

        JsonNode one = data(get("/masters/schools/" + id));
        assertThat(one.path("schoolCode").asText()).isEqualTo("SCH-001");
        assertThat(one.path("city").asText()).isEqualTo("진주");
        assertThat(one.path("region").asText()).isEqualTo("경남");
        assertThat(one.path("schoolName").asText()).isEqualTo("진주고등학교");
        assertThat(one.path("isSchool").asBoolean()).isTrue();
        assertThat(one.path("schoolType").asText()).isEqualTo("SCHOOL");
        assertThat(one.path("clientCategory").asText()).isEqualTo("특약점");

        // 수정: 학원으로 전환
        JsonNode upd = data(put("/masters/schools/" + id, Map.of(
                "custCode", "SCH-001", "schoolName", "진주학원", "isSchool", false,
                "schoolType", "HAKWON", "clientCategory", "기타학원")));
        assertThat(upd.path("isSchool").asBoolean()).isFalse();
        assertThat(upd.path("schoolType").asText()).isEqualTo("HAKWON");
        assertThat(upd.path("schoolName").asText()).isEqualTo("진주학원");
    }

    @Test
    @DisplayName("학교코드 중복 등록 → 400")
    void 학교코드_중복() {
        createId("/masters/schools", Map.of("schoolCode", "SCH-DUP", "schoolName", "중복학교"));
        JsonNode dup = post("/masters/schools", Map.of("schoolCode", "SCH-DUP", "schoolName", "중복학교2"));
        assertThat(dup.path("success").asBoolean()).isFalse();
        assertThat(dup.path("error").path("code").asText()).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("학교 목록 검색 + 엑셀 다운로드")
    void 학교_목록_엑셀() throws Exception {
        createId("/masters/schools", Map.of("schoolCode", "SCH-XL", "schoolName", "엑셀학교", "city", "부산"));
        JsonNode list = data(get("/masters/schools?keyword=SCH-XL"));
        assertThat(list.path("content").size()).isGreaterThanOrEqualTo(1);

        var resp = getBytes("/masters/schools/export");
        assertThat(resp.getStatusCode().is2xxSuccessful()).isTrue();
        try (var wb = org.apache.poi.ss.usermodel.WorkbookFactory.create(
                new java.io.ByteArrayInputStream(resp.getBody()))) {
            var sheet = wb.getSheetAt(0);
            assertThat(sheet.getSheetName()).isEqualTo("학교목록");
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("거래처코드");
        }
    }
}
