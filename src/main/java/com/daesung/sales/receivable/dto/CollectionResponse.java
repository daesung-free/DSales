package com.daesung.sales.receivable.dto;

import com.daesung.sales.receivable.entity.Collection;
import com.daesung.sales.receivable.entity.CollectionType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 수금 조회 응답.
 *
 * <p>구분이 두 축이다(정본 23p) — 수금구분(명목)·입금구분(형태).
 * 합치면 "어음으로 받은 도서대금"을 표현할 수 없다.
 */
public record CollectionResponse(
        @Schema(description = "수금 id") Long id,
        @Schema(description = "수금번호") String collectionNo,
        @Schema(description = "수금일자") LocalDate collDate,
        @Schema(description = "기장일자(회계 기표일)") LocalDate writeDate,
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "수금구분(무슨 명목). 기본 '도서대금'") String collKind,
        @Schema(description = "입금구분 코드(어떤 형태)") CollectionType collType,
        @Schema(description = "입금구분 명칭(현금/어음/선수금/대체)") String collTypeName,
        @Schema(description = "수금액") long collAmt,
        @Schema(description = "어음번호") String promissoryNo,
        @Schema(description = "어음 만기") LocalDate promissoryDue,
        @Schema(description = "은행명") String bankName,
        @Schema(description = "지점명") String branchName,
        @Schema(description = "비고") String memo
) {
    public static CollectionResponse from(Collection c) {
        return new CollectionResponse(
                c.getId(), c.getCollectionNo(), c.getCollDate(), c.getWriteDate(),
                c.getPartner().getId(), c.getPartner().getName(),
                c.getCollKind(), c.getCollType(), c.getCollType().label(), c.getCollAmt(),
                c.getPromissoryNo(), c.getPromissoryDue(), c.getBankName(), c.getBranchName(), c.getMemo());
    }
}
