package com.daesung.sales.inventory.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.BomWorkRequest;
import com.daesung.sales.inventory.dto.BomWorkResponse;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
import com.daesung.sales.inventory.dto.StockLedgerRow;
import com.daesung.sales.inventory.dto.TransferRequest;
import com.daesung.sales.inventory.dto.TransferResponse;
import com.daesung.sales.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주문/출고관리 - 재고(입고/이고/BOM). 실제 경로: /api/v1/stock. */
@Tag(name = "주문/출고관리 · 재고", description = "재고 엔진(로직 A): 입고/이고/BOM")
@RestController
@RequiredArgsConstructor
@RequestMapping("/stock")
public class InventoryController {

    private final InventoryService inventoryService;

    @Operation(summary = "일반 입고 등록",
            description = "인쇄소 등 → 물류창고 입고. 재고이벤트(INBOUND) 기록 + 재고 잔량 가산을 한 트랜잭션으로 처리")
    @PostMapping("/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InboundResponse> inbound(@Valid @RequestBody InboundRequest req) {
        return ApiResponse.success(inventoryService.inbound(req));
    }

    @Operation(summary = "단순 이고(창고 이동)",
            description = "출발창고 −qty(음수재고 방지) / 도착창고 +qty. 매출 미발생. 재고이벤트 2다리를 한 트랜잭션으로.")
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransferResponse> transfer(@Valid @RequestBody TransferRequest req) {
        return ApiResponse.success(inventoryService.transfer(req));
    }

    @Operation(summary = "세트 조립/해체(BOM)",
            description = "조립=완제품+/구성품−, 해체=반대. 구성품·비율은 상품 BOM 마스터에서 읽음. 음수재고 방지, 한 트랜잭션.")
    @PostMapping("/bom")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BomWorkResponse> bom(@Valid @RequestBody BomWorkRequest req) {
        return ApiResponse.success(inventoryService.bom(req));
    }

    @Operation(summary = "제품수불부 조회",
            description = "이월+입고+이고+BOM+폐기(+출고) = 현재재고 단일 공식 집계. "
                    + "이벤트합계(closing)와 캐시(inventory.qty) 대사(reconciled) 포함. "
                    + "기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/ledger")
    public ApiResponse<List<StockLedgerRow>> ledger(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "상품 id 필터") @RequestParam(required = false) Long productId,
            @Parameter(description = "창고 id 필터") @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.success(inventoryService.stockLedger(fromDate, toDate, productId, warehouseId));
    }
}
