package com.daesung.sales.inventory.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalResponse;
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

/** 주문/출고관리 - 폐기. 실제 경로: /api/v1/disposals. */
@Tag(name = "주문/출고관리 · 폐기", description = "연마감 폐기 등록(재고 즉시 차감)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/disposals")
public class DisposalController {

    private final InventoryService inventoryService;

    @Operation(summary = "폐기 등록",
            description = "등록 수량만큼 재고 즉시 차감(음수재고 방지) + 폐기번호(P) 채번. 한 트랜잭션.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DisposalResponse> dispose(@Valid @RequestBody DisposalRequest req) {
        return ApiResponse.success(inventoryService.dispose(req));
    }
}
