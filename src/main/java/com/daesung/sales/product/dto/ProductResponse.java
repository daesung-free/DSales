package com.daesung.sales.product.dto;

import com.daesung.sales.product.entity.ContentType;
import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.entity.SalesDivision;

/** 상품 응답 DTO. */
public record ProductResponse(
        Long id,
        String code,
        String name,
        ContentType contentType,
        boolean set,
        Integer price,
        boolean taxFree,
        String grade,

        String gradeName,   // 학년 표기(3 → 3학년). 저장값은 숫자뿐이라 서버가 붙여 준다
        String catCode,
        String catName,
        boolean useYn,
        /** 세부구분 코드(구 '매출구분'). 저장되는 값은 이것 하나뿐이다. */
        String salesDivision,
        /** 세부구분 명칭. 마스터에서 따라온다. */
        String salesDivisionName,
        /** 대분류 — 세부구분 마스터에서 <b>파생</b>. 상품에 따로 저장하지 않는다(매핑은 한 곳에만). */
        MajorCategory majorCategory,
        /** 대분류 명칭(모의고사·교재 …). 프론트가 매핑표를 들고 있지 않게 같이 준다. */
        String majorCategoryName,
        Integer productYear,
        String productType,
        Integer supplyRate,
        boolean ledgerVisible,

        boolean priceVisible,
        boolean webVisible,
        boolean stockManaged,

        @io.swagger.v3.oas.annotations.media.Schema(description = """
                마지막 수정 시각. **바뀐 도서만 받을 때 쓰는 기준**이다(프론트 요청 2026-10-20 B-1).
                `updatedSince` 에 지난번 받은 값 중 가장 큰 것을 그대로 넣으면 그 뒤 바뀐 것만 온다.
                ‼️등록만 하고 한 번도 안 고친 도서는 **비어 있다**(생성 시각은 `createdAt` 이다) —
                  그래서 서버는 `updatedSince` 를 **수정시각 또는 등록시각** 기준으로 본다.""")
        java.time.LocalDateTime updatedAt
) {
    /**
     * @param division 이 상품의 세부구분 마스터. null이면 미지정이거나 마스터에 없는 값 →
     *                 대분류는 '미분류'(null)로 나간다. 조용히 '기타'로 바꾸지 않는다 —
     *                 바꿔버리면 매핑이 빠진 상품을 담당자가 영영 못 찾는다.
     */
    public static ProductResponse from(Product p, SalesDivision division) {
        MajorCategory major = (division == null) ? null : division.getMajorCategory();
        return new ProductResponse(
                p.getId(), p.getCode(), p.getName(), p.getContentType(),
                p.isSet(), p.getPrice(), p.isTaxFree(), p.getGrade(), com.daesung.sales.common.code.MasterCodes.gradeName(p.getGrade()),
                p.getCatCode(), p.getCatName(), p.isUseYn(),
                p.getSalesDivision(),
                (division == null) ? null : division.getName(),
                major,
                (major == null) ? null : major.label(),
                p.getProductYear(), p.getProductType(), p.getSupplyRate(),
                p.isLedgerVisible(), p.isPriceVisible(), p.isWebVisible(), p.isStockManaged(),
                p.getUpdatedAt());
    }
}
