package com.daesung.sales.inventory.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
import com.daesung.sales.inventory.dto.TransferRequest;
import com.daesung.sales.inventory.dto.TransferResponse;
import com.daesung.sales.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
}
