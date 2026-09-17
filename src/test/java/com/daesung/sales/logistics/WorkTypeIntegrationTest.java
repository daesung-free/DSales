package com.daesung.sales.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.logistics.service.WorkTypeService;
import com.daesung.sales.support.IntegrationTestSupport;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;

/**
 * 작업구분 관리(36p) 회귀 고정. 근거: 발주처 회신 2026-08-21 +
 * 첨부 「물류비용등록_수정요청안_Page36_260821.pdf」①②.
 *
 * <p>★DSRE 연동이 꺼진 환경(테스트 기본)에서는 <b>마스터 관리</b>만 검증한다.
 * 일괄 반영은 DSRE2 단가 행을 상대하므로 복제본이 붙은 환경에서 확인한다.
 * 여기서 지키려는 것은 관리형 값의 규칙이다 —
 * PACKTYPE 불변 · 중복 거부 · 쓰는 곳이 있으면 삭제 대신 비활성.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@DisplayName("작업구분 관리(36p)")
class WorkTypeIntegrationTest extends IntegrationTestSupport {

    /** ‼️DSRE가 꺼진 환경이라 단가 HTTP 경로를 탈 수 없다 — 규칙 자체는 서비스에서 고정한다. */
    @org.springframework.beans.factory.annotation.Autowired
    private WorkTypeService workTypeService;

    @BeforeAll
    void auth() {
        token();
    }

    @Test
    @DisplayName("레거시 3종이 기본으로 들어 있다 — PACKTYPE 숫자에 이름이 붙는다")
    void 기본_3종() {
        JsonNode list = data(get("/masters/work-types"));

        assertThat(list.size()).isGreaterThanOrEqualTo(3);
        JsonNode first = byPackType(list, 1);
        assertThat(first.path("name").asText()).isEqualTo("반별봉투");
        assertThat(first.path("paper").asInt()).isEqualTo(50);

        // DSRE2 주석에만 있던 뜻(1:반별봉투, 2:개별봉투, 3:개별봉투(예외))이 데이터가 됐다
        assertThat(byPackType(list, 2).path("name").asText()).isEqualTo("개별봉투");
        assertThat(byPackType(list, 3).path("name").asText()).isEqualTo("개별봉투(SET)");
    }

    @Test
    @DisplayName("작업구분은 고정값이 아니다 — 4번을 새로 만들고 고칠 수 있다")
    void 관리형_값() {
        int packType = 40 + (int) (System.nanoTime() % 50);
        long id = data(post("/masters/work-types", Map.of(
                "packType", packType, "name", "개별봉투(대형)",
                "paper", 60, "omr", 60, "etc", 60,
                "label", 120, "basic", 120, "trade", 120, "sortOrder", 90))).path("id").asLong();

        JsonNode created = byPackType(data(get("/masters/work-types?includeUnused=true")), packType);
        assertThat(created.path("name").asText()).isEqualTo("개별봉투(대형)");
        assertThat(created.path("label").asInt()).isEqualTo(120);

        // 이름·단가는 고칠 수 있다
        data(put("/masters/work-types/" + id, Map.of(
                "name", "개별봉투(특대)", "paper", 70, "omr", 70, "etc", 70,
                "label", 140, "basic", 140, "trade", 140)));
        JsonNode updated = byPackType(data(get("/masters/work-types?includeUnused=true")), packType);
        assertThat(updated.path("name").asText()).isEqualTo("개별봉투(특대)");
        assertThat(updated.path("paper").asInt()).isEqualTo(70);

        // ‼️PACKTYPE은 바뀌지 않는다 — 단가 행과 잇는 유일한 키다
        data(put("/masters/work-types/" + id, Map.of(
                "packType", 999, "name", "개별봉투(특대)", "paper", 70, "omr", 70, "etc", 70,
                "label", 140, "basic", 140, "trade", 140)));
        assertThat(byPackType(data(get("/masters/work-types?includeUnused=true")), packType)
                .path("packType").asInt()).isEqualTo(packType);
    }

    @Test
    @DisplayName("같은 PACKTYPE으로 두 번 등록하면 거부한다 — 한 숫자에 두 뜻이 붙으면 안 된다")
    void packType_중복_거부() {
        JsonNode r = post("/masters/work-types", Map.of(
                "packType", 1, "name", "중복시도",
                "paper", 1, "omr", 1, "etc", 1, "label", 1, "basic", 1, "trade", 1));

        assertThat(r.path("success").asBoolean()).isFalse();
        assertThat(r.path("error").path("code").asText()).isEqualTo("INVALID_INPUT");
    }

    @Test
    @DisplayName("DSRE가 꺼져 있으면 삭제하지 않고 비활성으로 둔다 — 사용 여부를 알 수 없다")
    void 사용여부_모르면_비활성() {
        int packType = 100 + (int) (System.nanoTime() % 50);
        long id = data(post("/masters/work-types", Map.of(
                "packType", packType, "name", "임시구분",
                "paper", 1, "omr", 1, "etc", 1, "label", 1, "basic", 1, "trade", 1))).path("id").asLong();

        // 단가 행은 DSRE2에 있다. 연동이 꺼져 있으면 "쓰는 곳이 없다"고 단정할 수 없으므로
        // 지우지 않는다 — 지웠는데 실제로 쓰고 있었다면 그 PACKTYPE 숫자의 뜻이 사라진다.
        assertThat(data(del("/masters/work-types/" + id)).asBoolean())
                .as("삭제가 아니라 비활성").isFalse();

        // 행은 남아 있고(관리 목록엔 보임) 기본 목록에서만 빠진다
        assertThat(packTypes(data(get("/masters/work-types?includeUnused=true")))).contains(packType);
        assertThat(packTypes(data(get("/masters/work-types")))).doesNotContain(packType);
    }

    @Test
    @DisplayName("★DSRE가 꺼져 있어도 우리 상품 단가는 반영된다 — 대신 반쪽인 걸 숨기지 않는다")
    void 연동_꺼짐_일괄반영() {
        int packType = 150 + (int) (System.nanoTime() % 50);
        long id = data(post("/masters/work-types", Map.of(
                "packType", packType, "name", "반영시도",
                "paper", 1, "omr", 1, "etc", 1, "label", 1, "basic", 1, "trade", 1))).path("id").asLong();

        JsonNode d = data(post("/masters/work-types/" + id + "/apply", Map.of()));

        // ‼️예전엔 여기서 400을 던졌다. 그러면 DSRE가 없는 환경에서 **우리 상품 단가까지**
        //   통째로 반영 불가였다. 지금은 우리 쪽은 반영하고 DSRE 쪽은 건너뛴다.
        assertThat(d.path("dsreApplied").asBoolean())
                .as("DSRE는 못 건드렸다는 걸 화면이 알아야 한다").isFalse();
        assertThat(d.path("appliedDtlCds")).as("DSRE 시행 단가는 하나도 안 건드렸다").isEmpty();
    }

    @Test
    @DisplayName("★일괄반영 미리보기는 아무것도 바꾸지 않는다")
    void 미리보기_경로() {
        int packType = 200 + (int) (System.nanoTime() % 50);
        long id = data(post("/masters/work-types", Map.of(
                "packType", packType, "name", "미리보기대상",
                "paper", 1, "omr", 1, "etc", 1, "label", 1, "basic", 1, "trade", 1))).path("id").asLong();

        JsonNode d = data(get("/masters/work-types/" + id + "/apply/preview"));

        assertThat(d.path("preview").asBoolean()).as("★안 바꿨다는 표시").isTrue();
        assertThat(d.path("packType").asInt()).isEqualTo(packType);
    }

    @Test
    @DisplayName("★물류단가의 작업구분은 work_type이 기준이다 — 1~3에 갇혀 있지 않다")
    void 단가_작업구분_기준() {
        int packType = 250 + (int) (System.nanoTime() % 50);
        data(post("/masters/work-types", Map.of(
                "packType", packType, "name", "단가기준검증",
                "paper", 1, "omr", 1, "etc", 1, "label", 1, "basic", 1, "trade", 1)));

        // 새로 만든 4번 이상도 단가가 받아야 한다.
        // ‼️예전엔 단가 DTO에 @Max(3)이 박혀 있어, 작업구분은 만들어지는데 그 번호로 단가를 넣으면 400이었다.
        assertThatCode(() -> workTypeService.assertRegisteredPackType(packType))
                .as("등록된 작업구분이면 통과").doesNotThrowAnyException();

        // 등록 안 된 값은 거부하되, 무엇이 되는지 알려줘야 한다 — "안 된다"만 하면 담당자가 다음 수를 못 둔다.
        assertThatThrownBy(() -> workTypeService.assertRegisteredPackType(99_999))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("등록되지 않은 작업구분")
                .hasMessageContaining("반별봉투");

        // 벌크는 "미지정 = 기존값 유지"라 null이 정상 입력이다.
        assertThatCode(() -> workTypeService.assertRegisteredPackType(null))
                .as("미지정은 유지 의미").doesNotThrowAnyException();
    }

    private JsonNode byPackType(JsonNode list, int packType) {
        for (JsonNode w : list) {
            if (w.path("packType").asInt() == packType) {
                return w;
            }
        }
        throw new AssertionError("PACKTYPE=" + packType + " 없음: " + list);
    }

    private java.util.List<Integer> packTypes(JsonNode list) {
        java.util.List<Integer> out = new java.util.ArrayList<>();
        list.forEach(w -> out.add(w.path("packType").asInt()));
        return out;
    }
}
