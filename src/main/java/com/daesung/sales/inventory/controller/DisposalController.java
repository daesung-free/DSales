package com.daesung.sales.inventory.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalResponse;
import com.daesung.sales.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Parameter;
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

    @Operation(summary = "폐기 내역 조회(10p)",
            description = """
                    등록된 폐기 전표를 최근순으로. 기간·도서·창고로 좁힐 수 있다(전부 선택).

                    ★**등록만 되고 조회가 없었다.** 폐기는 재고를 깎는 전표라
                    "무엇을 언제 왜 버렸는지"를 되짚을 수 없으면 재고가 안 맞을 때
                    원인을 찾을 방법이 없다.

                    수량은 **양수**로 준다(원장에는 음수로 기록된다 — 재고를 깎으므로).""")
    @GetMapping
    public ApiResponse<java.util.List<com.daesung.sales.inventory.dto.DisposalRecordRow>> list(
            @Parameter(description = "폐기일 시작(yyyy-MM-dd). 미지정=전체")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "폐기일 종료(yyyy-MM-dd). 미지정=전체")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "도서(상품) id 필터") @RequestParam(required = false) Long productId,
            @Parameter(description = "창고 id 필터") @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.success(inventoryService.disposals(fromDate, toDate, productId, warehouseId));
    }
}
