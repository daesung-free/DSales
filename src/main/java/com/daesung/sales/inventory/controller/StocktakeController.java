package com.daesung.sales.inventory.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.StocktakeRequest;
import com.daesung.sales.inventory.dto.StocktakeResponse;
import com.daesung.sales.inventory.service.StocktakeService;
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

/** 물류/작업 - 재고실사. 실제 경로: /api/v1/stock/stocktakes. */
@Tag(name = "물류/작업 · 재고실사", description = "실물 재고 대조 → 차이 조정(ADJUST). 수불부·단일공식 유지")
@RestController
@RequiredArgsConstructor
@RequestMapping("/stock/stocktakes")
public class StocktakeController {

    private final StocktakeService stocktakeService;

    @Operation(summary = "재고실사 등록+적용",
            description = "창고별 상품 실물수량 입력 → 시스템(캐시) 대조 → 차이만큼 재고 조정(ADJUST 이벤트). "
                    + "조정 후 캐시=실물, 수불부에도 반영. 실사 이력 보존. 한 트랜잭션.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<StocktakeResponse> register(@Valid @RequestBody StocktakeRequest req) {
        return ApiResponse.success(stocktakeService.register(req));
    }
}
