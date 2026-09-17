package com.daesung.sales.logistics.dto;

import com.daesung.sales.logistics.entity.ProductLogisRate;
import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 매출프로그램 상품 물류단가 1행(36p). */
@Schema(name = "ProductLogisRateResponse", description = "상품별 물류단가")
public record ProductLogisRateResponse(
        @Schema(description = "단가 id") Long id,
        @Schema(description = "상품 id") Long productId,
        @Schema(description = "상품코드") String productCode,
        @Schema(description = "상품명") String productName,
        @Schema(description = "분류코드") String catCode,
        @Schema(description = "작업구분(PACKTYPE)") int packType,
        @Schema(description = "작업구분명") String workTypeName,
        @Schema(description = "시험지 단가") int paper,
        @Schema(description = "OMR 단가") int omr,
        @Schema(description = "단행본 단가") int etc,
        @Schema(description = "라벨 단가") int label,
        @Schema(description = "기본작업비(인별)") int basic,
        @Schema(description = "출고비(인별)") int trade,
        @Schema(description = "여분포함") String bSpare,

        @Schema(description = """
                개별 수정됨 — **작업구분 일괄반영이 이 행을 건너뛴다.**
                담당자가 일부러 다른 값을 넣은 행이 조용히 덮이면 잘못된 단가로 청구된다.""")
        boolean overridden,

        @Schema(description = """
                단독 관리 상품 여부(`products.price_visible`).
                **false면 이 단가가 안 쓰일 수 있다** — DSRE 병행 상품은 DSRE 기준이 산다.""")
        boolean priceVisible
) {
    public static ProductLogisRateResponse from(ProductLogisRate r, String workTypeName) {
        return new ProductLogisRateResponse(r.getId(),
                r.getProduct().getId(), r.getProduct().getCode(), r.getProduct().getName(),
                r.getProduct().getCatCode(),
                r.getPackType(), workTypeName,
                r.getPaper(), r.getOmr(), r.getEtc(), r.getLabel(), r.getBasic(), r.getTrade(),
                r.getBSpare(), r.isOverridden(), r.getProduct().isPriceVisible());
    }

    /** 등록·수정 결과 + 경고. 경고는 막지 않되 담당자가 알아야 하는 것을 담는다. */
    @Schema(name = "ProductLogisRateResult", description = "등록·수정 결과(경고 포함)")
    public record Result(
            @Schema(description = "단가") ProductLogisRateResponse rate,
            @Schema(description = "경고 — 비어 있으면 이상 없음") List<String> warnings
    ) {
    }
}
