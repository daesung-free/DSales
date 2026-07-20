package com.daesung.sales.product.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 상품(도서) 관리. 실제 경로: /api/v1/masters/products. */
@Tag(name = "기초관리 · 상품", description = "도서/상품 마스터 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/products")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 목록 조회", description = "페이징·정렬(page/size/sort) 지원")
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(@ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(productService.findAll(pageReq.toPageable()));
    }

    @Operation(summary = "상품 상세 조회", description = "id로 단건 조회. 없으면 404")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.success(productService.findById(id));
    }

    @Operation(summary = "상품 등록", description = "상품코드 중복 시 400 반환")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductCreateRequest req) {
        return ApiResponse.success(productService.create(req));
    }
}
