package com.daesung.sales.product.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.dto.ProductUpdateRequest;
import com.daesung.sales.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 상품(도서) 관리. 실제 경로: /api/v1/masters/products. */
@Tag(name = "기초관리 · 상품", description = "도서/상품 마스터 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/products")
public class ProductController {

    private final ProductService productService;

    @Operation(summary = "상품 목록 조회", description = "keyword(코드/명 부분일치)로 검색, 페이징·정렬 지원")
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @Parameter(description = "검색어(상품코드 또는 상품명 부분일치)") @RequestParam(required = false) String keyword,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(productService.findAll(keyword, pageReq.toPageable()));
    }

    @Operation(summary = "상품 상세 조회", description = "id로 단건 조회. 없으면 404")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.success(productService.findById(id));
    }

    @Operation(summary = "상품 등록", description = "상품코드 중복 시 400 반환")
    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductCreateRequest req) {
        return ApiResponse.success(productService.create(req));
    }

    @Operation(summary = "상품 수정", description = "코드는 불변. 없으면 404")
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductUpdateRequest req) {
        return ApiResponse.success(productService.update(id, req));
    }

    @Operation(summary = "상품 삭제(비활성화)", description = "물리삭제가 아니라 useYn=false 논리삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.deactivate(id);
        return ApiResponse.success();
    }
}
