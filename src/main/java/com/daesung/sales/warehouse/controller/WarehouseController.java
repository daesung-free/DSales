package com.daesung.sales.warehouse.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.warehouse.dto.WarehouseCreateRequest;
import com.daesung.sales.warehouse.dto.WarehouseResponse;
import com.daesung.sales.warehouse.dto.WarehouseUpdateRequest;
import com.daesung.sales.warehouse.service.WarehouseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 창고 관리. 실제 경로: /api/v1/masters/warehouses. */
@Tag(name = "기초관리 · 창고", description = "창고 마스터 관리 (물류/위탁)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/warehouses")
public class WarehouseController {

    private final WarehouseService warehouseService;

    @Operation(summary = "창고 목록 조회", description = "keyword(코드/명 부분일치)로 검색, 페이징·정렬 지원")
    @GetMapping
    public ApiResponse<PageResponse<WarehouseResponse>> list(
            @Parameter(description = "검색어(창고코드 또는 창고명 부분일치)") @RequestParam(required = false) String keyword,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(warehouseService.findAll(keyword, pageReq.toPageable()));
    }

    @Operation(summary = "창고 상세 조회", description = "id로 단건 조회. 없으면 404")
    @GetMapping("/{id}")
    public ApiResponse<WarehouseResponse> get(@PathVariable Long id) {
        return ApiResponse.success(warehouseService.findById(id));
    }

    @Operation(summary = "창고 등록", description = "창고코드 중복 시 400 반환")
    @PostMapping
    public ApiResponse<WarehouseResponse> create(@Valid @RequestBody WarehouseCreateRequest req) {
        return ApiResponse.success(warehouseService.create(req));
    }

    @Operation(summary = "창고 수정", description = "코드는 불변. 없으면 404")
    @PutMapping("/{id}")
    public ApiResponse<WarehouseResponse> update(@PathVariable Long id,
                                                 @Valid @RequestBody WarehouseUpdateRequest req) {
        return ApiResponse.success(warehouseService.update(id, req));
    }
}
