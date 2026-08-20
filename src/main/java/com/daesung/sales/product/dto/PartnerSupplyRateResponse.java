package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.PartnerSupplyRate;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 거래처별 대분류 공급률 응답(34p). 정본 데이터 항목 —
 * 거래처코드·거래처명·거래처구분·Web게시·공급률·할인액·사용여부.
 */
public record PartnerSupplyRateResponse(
        @Schema(description = "매핑 id") Long id,
        @Schema(description = "거래처 id") Long partnerId,
        @Schema(description = "거래처코드") String partnerCode,
        @Schema(description = "거래처명") String partnerName,
        @Schema(description = "거래처구분(특약점/기타학원/B2B/대성/자사몰)") String clientCategory,
        @Schema(description = "대분류 코드") MajorCategory majorCategory,
        @Schema(description = "대분류 명칭") String majorCategoryName,
        @Schema(description = "공급률(%)") Integer supplyRate,
        @Schema(description = "할인액(원). 금액 계산 미반영") Integer discountAmount,
        @Schema(description = "Web게시여부") boolean webVisible,
        @Schema(description = "사용여부") boolean useYn
) {
    public static PartnerSupplyRateResponse from(PartnerSupplyRate m) {
        return new PartnerSupplyRateResponse(
                m.getId(), m.getPartner().getId(), m.getPartner().getCode(), m.getPartner().getName(),
                m.getPartner().getClientCategory(),
                m.getMajorCategory(), m.getMajorCategory().label(),
                m.getSupplyRate(), m.getDiscountAmount(), m.isWebVisible(), m.isUseYn());
    }
}
