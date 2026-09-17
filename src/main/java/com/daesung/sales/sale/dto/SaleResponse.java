package com.daesung.sales.sale.dto;

import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.SalesDivision;
import com.daesung.sales.sale.entity.Sale;
import io.swagger.v3.oas.annotations.media.Schema;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.salestype.entity.TradeClass;
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
        String catCode,           // 분류코드(12p). ‼️대분류와 다른 축이다 — 아래 majorCategory 참고
        String catName,           // 분류명
        @Schema(description = """
                세부구분(구 '매출구분'). 발주처 회신 2026-08-20으로 명칭이 바뀌었다 —
                7p·12p에서 '대분류' 컬럼으로 표기하던 값이 실제로는 이것이다.""")
        String salesDivision,
        @Schema(description = "세부구분 명칭") String salesDivisionName,
        @Schema(description = "대분류 코드(집계 기준). 세부구분 마스터에서 파생, 미지정은 null")
        MajorCategory majorCategory,
        @Schema(description = "대분류 명칭(모의고사·교재 …)") String majorCategoryName,
        Long productId,
        String productCode,       // 도서코드
        String productName,       // 도서명
        Integer bookRound,        // 회차(12p)
        SalesType salesType,
        ShipmentType shipmentType,
        @Schema(description = """
                출고유형 명칭(정상출고/위탁출고/증정용/교사용/반품/취소).
                ★코드(enum)와 한글을 <b>둘 다</b> 준다 — 화면마다 라벨표를 만들면 언젠가 갈린다.
                실제로 엑셀에 `NORMAL_SHIP`이 그대로 찍히고 있었다(2026-09-17 지적).""")
        String shipmentTypeName,
        @Schema(description = """
                거래분류 코드(표준 4축 중 첫째). 매출 원장에서는 SALES/FREE/RETURN만 나온다 —
                INBOUND(입고)·DISPOSE(폐기)는 재고 원장의 거래다.
                **구분(상세)에서 파생**되며 따로 저장하지 않는다(두 값이 어긋날 여지를 두지 않는다).""")
        TradeClass tradeClass,
        @Schema(description = "거래분류 명칭(매출/무상/반품)") String tradeClassName,
        @Schema(description = "구분(상세) — 회계구분 SALE/FREE/RETURN") SalesCategory salesCategory,
        Integer unitPrice,
        Integer supplyRate,
        @Schema(description = """
                적용된 권당 할인액(34p). 값이 있으면 공급가액 = (정가−할인액)×수량이고
                공급률은 금액에 쓰이지 않았다는 뜻이다.""")
        Integer discountAmount,
        int qty,
        Long supplyAmount,
        Long tax,
        Long totalAmount,
        boolean canceled,
        @Schema(description = "출고 창고 id") Long warehouseId,
        @Schema(description = "출고 창고명(7p 재고위치). 이 컬럼 도입 전 매출은 비어 있다") String warehouseName,
        String memo
) {
    /**
     * @param division 이 매출 상품의 세부구분 마스터(null=미지정). 대분류는 여기서 파생된다 —
     *                 매출에 대분류를 복사해두지 않는 이유는 매핑이 바뀌면 과거 조회가 어긋나기 때문이다.
     */
    public static SaleResponse from(Sale s, SalesDivision division) {
        MajorCategory major = (division == null) ? null : division.getMajorCategory();
        return new SaleResponse(
                s.getId(), s.getSalesNo(), s.getSalesDate(),
                s.getPartner().getId(), s.getPartner().getCode(), s.getPartner().getName(),
                s.getPartner().getCityName(), s.getPartner().getName1(),
                s.getPartner().getRegion(), s.getPartner().getClientCategory(),
                s.getSchoolCode(), s.getSchoolName(), s.getProduct().getGrade(),
                s.getProduct().getCatCode(), s.getProduct().getCatName(),
                s.getProduct().getSalesDivision(),
                (division == null) ? null : division.getName(),
                major, (major == null) ? null : major.label(),
                s.getProduct().getId(), s.getProduct().getCode(), s.getProduct().getName(), s.getBookRound(),
                s.getSalesType(), s.getShipmentType(),
                ShipmentType.labelOf(s.getShipmentType()),
                TradeClass.of(s.getSalesCategory()),
                (s.getSalesCategory() == null) ? null : TradeClass.of(s.getSalesCategory()).label(),
                s.getSalesCategory(),
                s.getUnitPrice(), s.getSupplyRate(), s.getDiscountAmount(), s.getQty(),
                s.getSupplyAmount(), s.getTax(), s.getTotalAmount(),
                s.isCanceled(),
                (s.getWarehouse() == null) ? null : s.getWarehouse().getId(),
                (s.getWarehouse() == null) ? null : s.getWarehouse().getName(),
                s.getMemo());
    }
}
