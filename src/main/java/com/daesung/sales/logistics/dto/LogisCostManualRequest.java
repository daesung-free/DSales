package com.daesung.sales.logistics.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/**
 * 수기 물류작업비 등록·수정(28p 에디팅).
 *
 * <p>수정 시 <b>접수일자·신청번호·시행코드는 무시된다</b> — 귀속 축이라 바꾸면 다른 달로 옮겨진다.
 * 옮기려면 지우고 다시 넣는다(그래야 원래 달에서 사라진 이유가 삭제 기록으로 남는다).
 */
@Schema(name = "LogisCostManualRequest", description = "수기 물류작업비")
public record LogisCostManualRequest(

        @Schema(description = "접수일자(귀속 축, 등록 시 필수)", example = "2026-07-15",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate reqDate,

        @Schema(description = "신청번호. 대응 신청이 없으면 비워 둔다", example = "78331") Integer reqCd,
        @Schema(description = "상품코드") String productCode,
        @Schema(description = "상품명") String productName,
        @Schema(description = "학년") String grade,
        @Schema(description = "시행코드. 없으면 비워 둔다") Integer dtlCd,
        @Schema(description = "시행명") String detailName,
        @Schema(description = "거래처코드") String partnerCode,
        @Schema(description = "거래처명") String partnerName,

        @Schema(description = "자재 합") @PositiveOrZero long materialQty,
        @Schema(description = "시험지 합") @PositiveOrZero long paperQty,
        @Schema(description = "시험지 금액") @PositiveOrZero long paperAmount,
        @Schema(description = "OMR 합") @PositiveOrZero long omrQty,
        @Schema(description = "OMR 금액") @PositiveOrZero long omrAmount,
        @Schema(description = "기타 합") @PositiveOrZero long etcQty,
        @Schema(description = "기타 금액") @PositiveOrZero long etcAmount,
        @Schema(description = "인원") @PositiveOrZero int inwon,
        @Schema(description = "기본작업비") @PositiveOrZero long basicAmount,
        @Schema(description = "출고비") @PositiveOrZero long tradeAmount,

        @Schema(description = "구분 S(일반)/A(사고). 자동계산분과 같은 축으로 걸러진다", example = "S")
        String applyGn,
        @Schema(description = "메모 — 왜 수기로 넣었는지") String memo
) {
}
