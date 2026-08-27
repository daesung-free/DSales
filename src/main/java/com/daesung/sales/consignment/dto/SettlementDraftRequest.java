package com.daesung.sales.consignment.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;
import java.util.List;

/**
 * 위탁정산 임시저장 요청. 근거: 정본 13p 2단계(임시저장 → 매출확정등록) +
 * 프론트 {@code saveSettlementDraft(input, salesDate)}.
 *
 * <p>필드명은 <b>화면이 이미 보내고 있는 그대로</b>다({@code input} / {@code pendingId}).
 * 서버 쪽 이름(settlements·consignmentOutId)으로 바꾸면 화면을 고쳐야 하는데,
 * 임시저장은 아직 붙지 않은 신규 경로라 화면 계약을 따르는 편이 손이 덜 간다.
 */
public record SettlementDraftRequest(

        @Schema(description = "매출 인식일(확정하면 이 날짜로 매출이 선다)", example = "2026-06-22",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate salesDate,

        @Schema(description = "정산할 미결 라인", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Valid List<Item> input,

        @Schema(description = "메모") String memo
) {
    @Schema(name = "SettlementDraftItem")
    public record Item(

            @Schema(description = "미결 id(화면의 pendingId = consignment_out id)", example = "1",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long pendingId,

            @Schema(description = "이번에 정산할 수량", example = "300",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @Positive int settleQty,

            @Schema(description = "정가. 미입력 시 도서 마스터 정가", example = "20000")
            Integer unitPrice,

            @Schema(description = "공급률(%). 미입력 시 거래처별 단가 매핑", example = "75")
            Integer supplyRate,

            @Schema(description = "세액(선택). 자동산출하지 않으며 미입력 시 0", example = "0")
            @PositiveOrZero Integer tax
    ) {
    }
}
