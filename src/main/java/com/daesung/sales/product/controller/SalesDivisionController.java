package com.daesung.sales.product.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.product.dto.MajorCategoryResponse;
import com.daesung.sales.product.dto.SalesDivisionRequest;
import com.daesung.sales.product.dto.SalesDivisionResponse;
import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.service.SalesDivisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기초관리 - 세부구분(구 '매출구분'). 실제 경로: /api/v1/masters/sales-divisions.
 *
 * <p>상품 분류는 두 층이다 — <b>대분류(5종 고정, 집계 기준)</b>와 그 아래 <b>세부구분</b>.
 * 대분류는 늘어나지 않아 API가 없고(조회만), 세부구분만 담당자가 직접 추가·수정·삭제한다.
 * 근거: 발주처 회신 2026-08-20.
 */
@Tag(name = "기초관리 · 세부구분",
        description = "상품 세부구분(구 매출구분) 관리 + 대분류 목록. 집계는 대분류, 드릴다운은 세부구분")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/sales-divisions")
public class SalesDivisionController {

    private final SalesDivisionService salesDivisionService;

    @Operation(summary = "세부구분 목록 조회",
            description = "대분류 → 정렬순서 → 코드 순. 기본은 사용중인 것만 준다.")
    @GetMapping
    public ApiResponse<List<SalesDivisionResponse>> list(
            @Parameter(description = "대분류 필터. 미지정=전체")
            @RequestParam(required = false) MajorCategory majorCategory,
            @Parameter(description = "미사용(use_yn=false) 포함 여부. 관리 화면에서만 true")
            @RequestParam(required = false, defaultValue = "false") boolean includeUnused) {
        return ApiResponse.success(salesDivisionService.findAll(majorCategory, includeUnused));
    }

    @Operation(summary = "대분류 목록 조회(하위 세부구분 포함)",
            description = """
                    상품 등록(32p)이 **대분류를 먼저 고르고 그 아래 세부구분을 입력**하는 순서라,
                    두 단계를 한 번에 채울 수 있게 묶어서 준다.

                    · 대분류는 **5종 고정**(모의고사·교재·기타고사·특강·기타)이라 등록·수정이 없다.
                    · **IC는 목록에 없다** — 발주처 확정 "현재 미사용이나 데이터는 보존해야 하니 화면상 숨김".
                      숨기는 것이지 지우는 게 아니라, IC로 저장된 기존 데이터는 조회·집계에 그대로 남는다.""")
    @GetMapping("/major-categories")
    public ApiResponse<List<MajorCategoryResponse>> majorCategories(
            @RequestParam(required = false, defaultValue = "false") boolean includeUnused) {
        return ApiResponse.success(salesDivisionService.majorCategories(includeUnused));
    }

    @Operation(summary = "세부구분 등록",
            description = "코드는 이후 바꿀 수 없다 — 상품이 이 값으로 연결되기 때문. 명칭은 자유롭게 수정 가능.")
    @PostMapping
    public ApiResponse<SalesDivisionResponse> create(@Valid @RequestBody SalesDivisionRequest req) {
        return ApiResponse.success(salesDivisionService.create(req));
    }

    @Operation(summary = "세부구분 수정",
            description = "명칭·소속 대분류·사용여부·정렬순서를 고친다. **코드는 무시된다**(불변).")
    @PutMapping("/{id}")
    public ApiResponse<SalesDivisionResponse> update(@PathVariable Long id,
                                                     @Valid @RequestBody SalesDivisionRequest req) {
        return ApiResponse.success(salesDivisionService.update(id, req));
    }

    @Operation(summary = "세부구분 삭제",
            description = """
                    **쓰는 상품이 있으면 지우지 않고 사용여부만 끈다**(응답 `false`).
                    지워버리면 그 상품들이 어느 대분류였는지 알 수 없게 되고 매출 집계에서 통째로 빠진다.
                    담당자 화면에서는 어느 쪽이든 목록에서 사라진 것으로 똑같이 보인다.

                    @return true=실제 삭제 / false=사용 중이라 비활성 처리""")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(salesDivisionService.delete(id));
    }
}
