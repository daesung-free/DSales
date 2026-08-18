package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 물류단가 <b>다건 일괄 수정</b>. 근거: 정본 구분값정리 [10.물류비용등록]
 * "선택 항목 일괄 수정(신규) — 체크박스 다중선택 → 시험지/OMR/단행본/라벨/기본작업비/출고비/작업구분 일괄 적용".
 *
 * <p><b>입력한 항목만 반영</b>하고 비운 칸은 기존 값을 유지한다.
 * 빈 칸이 0으로 덮이면 단가가 0이 되어 그 시행의 물류비가 통째로 0원으로 계산된다.
 */
@Schema(name = "LogisCostBulkRequest", description = "물류단가 일괄 수정")
public record LogisCostBulkRequest(

        @Schema(description = "대상 시행코드(DTL_CD) 목록", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Size(max = 1000, message = "한 번에 1000건까지 처리할 수 있습니다.")
        List<Integer> dtlCds,

        @Schema(description = "시험지 단가 — 미지정이면 유지", example = "50")
        @PositiveOrZero Integer paper,

        @Schema(description = "OMR 단가 — 미지정이면 유지", example = "50")
        @PositiveOrZero Integer omr,

        @Schema(description = "단행본 단가 — 미지정이면 유지", example = "50")
        @PositiveOrZero Integer etc,

        @Schema(description = "라벨 단가 — 미지정이면 유지", example = "0")
        @PositiveOrZero Integer label,

        @Schema(description = "기본작업비 — 미지정이면 유지", example = "100")
        @PositiveOrZero Integer basic,

        @Schema(description = "출고비 — 미지정이면 유지", example = "100")
        @PositiveOrZero Integer trade,

        @Schema(description = """
                작업구분(1 반별봉투 / 2 개별봉투 / 3 개별봉투SET) — 미지정이면 유지.
                ⚠️작업구분이 서로 다른 행을 함께 선택하면 거부된다(아래 설명 참고).""", example = "1")
        Integer packtype
) {
    /** 바꿀 항목이 하나도 없으면 무의미한 요청이다(전 건을 훑고 아무것도 안 한다). */
    public boolean hasNoField() {
        return paper == null && omr == null && etc == null && label == null
                && basic == null && trade == null && packtype == null;
    }
}
