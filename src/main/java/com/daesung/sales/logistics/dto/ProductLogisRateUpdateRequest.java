package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * 물류단가 개별 수정. <b>지정한 항목만</b> 바뀐다 —
 * 라벨만 고치려다 기본작업비가 0으로 덮이면 그 상품 물류비가 통째로 틀어진다.
 *
 * <p>‼️이 경로로 고친 행은 <b>예외로 표시</b>되어 이후 작업구분 일괄반영이 건너뛴다.
 */
@Schema(name = "ProductLogisRateUpdateRequest", description = "물류단가 개별 수정(지정 항목만)")
public record ProductLogisRateUpdateRequest(

        @Schema(description = """
                작업구분 변경 — 지정하면 **새 작업구분의 기준단가로 다시 채우고 예외 표시를 푼다**
                (아래 단가 항목과 함께 주면 단가 쪽이 이긴다).""", example = "2")
        @Positive Integer packType,

        @Schema(description = "시험지 단가 — 미지정이면 유지", example = "50") @PositiveOrZero Integer paper,
        @Schema(description = "OMR 단가 — 미지정이면 유지", example = "50") @PositiveOrZero Integer omr,
        @Schema(description = "단행본 단가 — 미지정이면 유지", example = "50") @PositiveOrZero Integer etc,
        @Schema(description = "라벨 단가 — 미지정이면 유지", example = "0") @PositiveOrZero Integer label,
        @Schema(description = "기본작업비 — 미지정이면 유지", example = "100") @PositiveOrZero Integer basic,
        @Schema(description = "출고비 — 미지정이면 유지", example = "100") @PositiveOrZero Integer trade,

        @Schema(description = "여분포함(Y/N) — 미지정이면 유지", example = "Y")
        @Pattern(regexp = "[YN]") String bSpare
) {
    /** 단가 항목을 하나도 안 줬는지 — 작업구분만 바꾸는 요청인지 가른다. */
    public boolean hasNoRateField() {
        return paper == null && omr == null && etc == null && label == null
                && basic == null && trade == null && bSpare == null;
    }
}
