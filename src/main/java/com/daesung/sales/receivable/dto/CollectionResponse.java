package com.daesung.sales.receivable.dto;

import com.daesung.sales.receivable.entity.Collection;
import com.daesung.sales.receivable.entity.CollectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/** 수금 조회 응답. */
public record CollectionResponse(
        @Schema(description = "수금 id") Long id,
        @Schema(description = "수금번호") String collectionNo,
        @Schema(description = "수금일자") LocalDate collDate,
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "수금유형") CollectionType collType,
        @Schema(description = "수금액") long collAmt,
        @Schema(description = "어음번호") String promissoryNo,
        @Schema(description = "어음 만기") LocalDate promissoryDue,
        @Schema(description = "은행명") String bankName,
        @Schema(description = "지점명") String branchName,
        @Schema(description = "비고") String memo
) {
    public static CollectionResponse from(Collection c) {
        return new CollectionResponse(
                c.getId(), c.getCollectionNo(), c.getCollDate(),
                c.getPartner().getId(), c.getPartner().getName(),
                c.getCollType(), c.getCollAmt(),
                c.getPromissoryNo(), c.getPromissoryDue(), c.getBankName(), c.getBranchName(), c.getMemo());
    }
}
