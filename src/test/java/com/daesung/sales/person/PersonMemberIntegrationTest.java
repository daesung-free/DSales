package com.daesung.sales.person;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 개인회원관리(구 IC) 회귀 고정.
 * 근거: 레거시 개인회원관리.vb 조회 조건(결제일 기간 + 학생ID/이름 + 상품명 + 연락처) + DSLab.personData.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("개인회원관리 통합테스트")
class PersonMemberIntegrationTest extends IntegrationTestSupport {

    private long create(String stId, String stName, String goods, String tel1, String payDate) {
        Map<String, Object> body = new HashMap<>();
        body.put("studentId", stId);
        body.put("studentName", stName);
        body.put("goodsName", goods);
        body.put("goodsCode", "ICF");
        body.put("tel1", tel1);
        body.put("payDate", payDate);
        body.put("fiscalYear", "2026");
        body.put("post", "13588");
        body.put("addr1", "경기 성남시 분당구 중앙공원로 1");
        body.put("addr2", "101동 202호");
        body.put("receiver", stName);
        body.put("manager", "본사");
        return createId("/masters/person-members", body);
    }

    @Test
    @DisplayName("등록·조회 — 레거시 personData 컬럼이 왕복한다")
    void 등록_조회() {
        long id = create("hong1234", "홍길동", "2026년 IC파이널(국수영)", "010-1111-2222", "2026-06-15T10:30:00");

        JsonNode m = data(get("/masters/person-members/" + id));
        assertThat(m.path("studentId").asText()).isEqualTo("hong1234");
        assertThat(m.path("studentName").asText()).isEqualTo("홍길동");
        assertThat(m.path("goodsName").asText()).isEqualTo("2026년 IC파이널(국수영)");
        assertThat(m.path("addr1").asText()).isEqualTo("경기 성남시 분당구 중앙공원로 1");
        assertThat(m.path("receiver").asText()).isEqualTo("홍길동");
        assertThat(m.path("manager").asText()).isEqualTo("본사");
        assertThat(m.path("inputDate").isMissingNode()).as("등록일 자동 기록").isFalse();
    }

    @Test
    @DisplayName("검색 — 결제일 기간·학생ID/이름·상품명·연락처가 각각 걸린다(레거시 조회조건)")
    void 검색조건() {
        create("kim0001", "김철수", "2026년 대성 MC", "010-3333-4444", "2026-03-10T09:00:00");
        create("lee0002", "이영희", "2026년 IC파이널", "02-555-6666", "2026-09-20T09:00:00");

        // 기간: 3월만
        JsonNode byPeriod = data(get("/masters/person-members?fromDate=2026-03-01&toDate=2026-03-31&size=100"));
        assertThat(byPeriod.path("content").findValuesAsText("studentId"))
                .contains("kim0001").doesNotContain("lee0002");

        // 학생이름 부분일치
        assertThat(data(get("/masters/person-members?keyword=영희&size=100")).path("content")
                .findValuesAsText("studentId")).containsExactly("lee0002");

        // 학생ID 부분일치(같은 파라미터가 ID·이름 둘 다 검색)
        assertThat(data(get("/masters/person-members?keyword=kim000&size=100")).path("content")
                .findValuesAsText("studentId")).containsExactly("kim0001");

        // 상품명 부분일치
        assertThat(data(get("/masters/person-members?goods=대성 MC&size=100")).path("content")
                .findValuesAsText("studentId")).containsExactly("kim0001");

        // 연락처는 연락처1·2를 함께 검색
        assertThat(data(get("/masters/person-members?tel=555-6666&size=100")).path("content")
                .findValuesAsText("studentId")).containsExactly("lee0002");
    }

    @Test
    @DisplayName("수정·삭제 — 삭제는 논리삭제라 조회에서만 빠지고 행은 남는다")
    void 수정_논리삭제() {
        long id = create("park0003", "박민수", "2026년 IC파이널", "010-7777-8888", "2026-05-05T09:00:00");

        Map<String, Object> upd = new HashMap<>();
        upd.put("studentId", "park0003");
        upd.put("studentName", "박민수");
        upd.put("goodsName", "2026년 IC파이널(수정)");
        upd.put("addr2", "202동 303호");
        put("/masters/person-members/" + id, upd);
        assertThat(data(get("/masters/person-members/" + id)).path("goodsName").asText())
                .isEqualTo("2026년 IC파이널(수정)");

        del("/masters/person-members/" + id);
        assertThat(get("/masters/person-members/" + id).path("success").asBoolean())
                .as("삭제 후 조회는 404").isFalse();
        assertThat(data(get("/masters/person-members?keyword=park0003&size=100")).path("content"))
                .as("목록에서도 제외").isEmpty();
    }
}
