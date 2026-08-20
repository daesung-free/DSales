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

        @Schema(description = "기장일자(회계 기표일). 수금일자와 다를 수 있다. 미입력 가능",
                example = "2026-06-30")
        LocalDate writeDate,

        @Schema(description = "거래처 id", example = "1", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotNull Long partnerId,

        @Schema(description = """
                수금구분 — **무슨 명목으로 받은 돈인가**(정본 23p 데이터 항목).
                미입력 시 `도서대금`. 레거시는 이 값을 화면에서 "도서대금"으로 고정하고 편집을 막아 두었다.""",
                example = "도서대금")
        String collKind,

        @Schema(description = """
                입금구분 — **어떤 형태로 들어왔는가**. CASH(현금)/PROMISSORY(어음)/PREPAY(선수금)/REPLACE(대체).
                어음번호·만기·은행/지점은 PROMISSORY일 때만 저장된다.""", example = "CASH",
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
