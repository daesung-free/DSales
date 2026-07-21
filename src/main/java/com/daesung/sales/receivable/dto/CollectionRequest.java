package com.daesung.sales.receivable.dto;

import com.daesung.sales.receivable.entity.CollectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import java.time.LocalDate;

/** 수금 등록 요청. 어음 정보는 collType=PROMISSORY일 때만 저장됨. */
public record CollectionRequest(

        @Schema(description = "수금일자", example = "2026-06-20", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull LocalDate collDate,

        @Schema(description = "거래처 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = "수금유형(CASH/PROMISSORY/PREPAY/REPLACE)", example = "CASH",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull CollectionType collType,

        @Schema(description = "수금액(원)", example = "1000000", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull @PositiveOrZero Long collAmt,

        @Schema(description = "어음번호(어음일 때)", example = "가12345678") String promissoryNo,
        @Schema(description = "어음 만기(어음일 때)", example = "2026-09-30") LocalDate promissoryDue,
        @Schema(description = "은행명(어음일 때)", example = "국민은행") String bankName,
        @Schema(description = "지점명(어음일 때)", example = "강남지점") String branchName,
        @Schema(description = "비고") String memo
) {
}
