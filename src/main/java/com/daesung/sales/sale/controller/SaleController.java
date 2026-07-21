package com.daesung.sales.sale.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.service.SaleService;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 매출관리. 실제 경로: /api/v1/sales. */
@Tag(name = "매출관리 · 매출", description = "매출 등록/취소/조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;

    @Operation(summary = "통합 매출 조회",
            description = "기간·회계구분·출고유형·거래처로 조회. 기본은 취소건 제외(includeCanceled=true면 포함)")
    @GetMapping
    public ApiResponse<PageResponse<SaleResponse>> list(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "회계구분(SALE/FREE/RETURN)") @RequestParam(required = false) SalesCategory salesCategory,
            @Parameter(description = "출고유형(6종)") @RequestParam(required = false) ShipmentType shipmentType,
            @Parameter(description = "거래처 id") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "취소건 포함 여부(기본 false)") @RequestParam(defaultValue = "false") boolean includeCanceled,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(saleService.search(
                startDate, endDate, salesCategory, shipmentType, partnerId, includeCanceled, pageReq.toPageable()));
    }

    @Operation(summary = "수기 매출 등록(일반)",
            description = "품목별 금액(정가×공급률/100×수량)·세액 산출 + 매출번호(I) 채번. "
                    + "출고유형→회계구분 자동. 위탁출고(CONSIGN_SHIP)는 불가(위탁정산 별도).")
    @PostMapping("/entries")
    public ApiResponse<SalesEntryResponse> createEntries(@Valid @RequestBody SalesEntryRequest req) {
        return ApiResponse.success(saleService.createEntries(req));
    }

    @Operation(summary = "매출 취소(논리 취소)",
            description = "원 매출을 삭제하지 않고 취소 표시. 이미 취소된 건은 400. 재고 복구는 주문/출고 통합 단계에서.")
    @PostMapping("/{id}/cancel")
    public ApiResponse<SaleResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(saleService.cancel(id));
    }
}
