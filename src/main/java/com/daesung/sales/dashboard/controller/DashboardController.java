package com.daesung.sales.dashboard.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dashboard.dto.DashboardResponse;
import com.daesung.sales.dashboard.dto.TargetRequest;
import com.daesung.sales.dashboard.service.DashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 매출관리 · 대시보드. 목표 대비 실적. 목표 입력은 재무/관리자(예산 권한). */
@Tag(name = "매출관리 · 대시보드", description = "매출목표 등록 + 목표 대비 실적·달성률·전년비")
@RestController
@RequestMapping("/dashboard")
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;

    @Operation(summary = "매출목표 등록/수정",
            description = "연·월·상품(미지정=전사) 목표금액 upsert. 예산 입력 권한(재무/관리자)만. "
                    + "정확한 입력 권한 정책은 발주처 확정 대기.")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @PostMapping("/targets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> setTarget(@Valid @RequestBody TargetRequest req) {
        dashboardService.upsertTarget(req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "매출 대시보드(목표 대비 실적)",
            description = "연도별 월 목표/실적(순매출)·달성률·전년 동월 대비 성장률 + 연간 합계. "
                    + "productId 미지정 시 전사(전사 목표 기준).")
    @GetMapping("/sales")
    public ApiResponse<DashboardResponse> sales(
            @Parameter(description = "연도", example = "2026") @RequestParam int year,
            @Parameter(description = "상품 id(미지정=전사)") @RequestParam(required = false) Long productId) {
        return ApiResponse.success(dashboardService.salesDashboard(year, productId));
    }
}
