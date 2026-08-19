package com.daesung.sales.sale.dto;

import com.daesung.sales.sale.entity.Sale;
import io.swagger.v3.oas.annotations.media.Schema;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;

/** 매출 상세/조회 응답 DTO. 통합매출조회(12p) 컬럼 전부 노출. */
public record SaleResponse(
        Long id,
        String salesNo,
        LocalDate salesDate,
        Long partnerId,
        String partnerCode,       // 거래처코드
        String partnerName,       // 거래처명2(합쳐진 풀네임)
        String partnerCityName,   // 도시명
        String partnerName1,      // 거래처명1(상호만)
        String region,            // 지역(12p)
        String clientCategory,    // 거래처구분(12p)
        String schoolCode,        // 학교코드(12p 학교구분명)
        String schoolName,        // 학교/학원명
        String grade,             // 학년(12p, 상품 마스터 속성)
        String catCode,           // 분류코드/대분류(12p)
        String catName,           // 분류명
        Long productId,
        String productCode,       // 도서코드
        String productName,       // 도서명
        Integer bookRound,        // 회차(12p)
        SalesType salesType,
        ShipmentType shipmentType,
        SalesCategory salesCategory,
        Integer unitPrice,
        Integer supplyRate,
        int qty,
        Long supplyAmount,
        Long tax,
        Long totalAmount,
        boolean canceled,
        @Schema(description = "출고 창고 id") Long warehouseId,
        @Schema(description = "출고 창고명(7p 재고위치). 이 컬럼 도입 전 매출은 비어 있다") String warehouseName,
        String memo
) {
    public static SaleResponse from(Sale s) {
        return new SaleResponse(
                s.getId(), s.getSalesNo(), s.getSalesDate(),
                s.getPartner().getId(), s.getPartner().getCode(), s.getPartner().getName(),
                s.getPartner().getCityName(), s.getPartner().getName1(),
                s.getPartner().getRegion(), s.getPartner().getClientCategory(),
                s.getSchoolCode(), s.getSchoolName(), s.getProduct().getGrade(),
                s.getProduct().getCatCode(), s.getProduct().getCatName(),
                s.getProduct().getId(), s.getProduct().getCode(), s.getProduct().getName(), s.getBookRound(),
                s.getSalesType(), s.getShipmentType(), s.getSalesCategory(),
                s.getUnitPrice(), s.getSupplyRate(), s.getQty(),
                s.getSupplyAmount(), s.getTax(), s.getTotalAmount(),
                s.isCanceled(),
                (s.getWarehouse() == null) ? null : s.getWarehouse().getId(),
                (s.getWarehouse() == null) ? null : s.getWarehouse().getName(),
                s.getMemo());
    }
}
