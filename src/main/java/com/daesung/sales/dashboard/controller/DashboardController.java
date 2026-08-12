package com.daesung.sales.dashboard.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dashboard.dto.DashboardResponse;
import com.daesung.sales.dashboard.dto.TargetRequest;
import com.daesung.sales.dashboard.dto.TargetResponse;
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
            description = "연·월(비우면 연간)·대상(전사/사업부문/상품) 금액 upsert. 예산 입력 권한(재무/관리자)만. "
                    + "정확한 입력 권한 정책은 발주처 확정 대기.")
    @PreAuthorize("hasAnyRole('ADMIN','FINANCE')")
    @PostMapping("/targets")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> setTarget(@Valid @RequestBody TargetRequest req) {
        dashboardService.upsertTarget(req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "매출 대시보드(목표 대비 실적)",
            description = """
                    연도별 월 목표/실적(순매출)·달성률·전년 동월 대비 성장률 + 연간 합계.

                    · 대상은 scope로 고른다 — COMPANY(전사, 기본) / DIVISION(사업부문) / PRODUCT(상품).
                    · **연간 목표만 등록된 경우 월 셀 목표는 0이고 연간 요약에만 잡힌다.**
                      연간 금액을 12로 나눠 뿌리면 있지도 않은 월 목표를 만들어내기 때문이다.
                    · 전년 실적은 우리 매출에서 계산하되, 그 해 매출이 통째로 없으면
                      저장된 확정 실적(entryType=ACTUAL)으로 채운다.""")
    @GetMapping("/sales")
    public ApiResponse<DashboardResponse> sales(
            @Parameter(description = "연도", example = "2026") @RequestParam int year,
            @Parameter(description = "대상 축(미지정=COMPANY 전사)")
            @RequestParam(required = false) com.daesung.sales.dashboard.entity.TargetScope scope,
            @Parameter(description = "사업부문명(scope=DIVISION)", example = "더프리미엄")
            @RequestParam(required = false) String scopeKey,
            @Parameter(description = "상품 id(scope=PRODUCT)") @RequestParam(required = false) Long productId) {
        return ApiResponse.success(dashboardService.salesDashboard(year, scope, scopeKey, productId));
    }

    @Operation(summary = "매출목표 목록",
            description = "연도의 등록된 목표·실적 전체(전사/사업부문/상품, 월별·연간). 관리 화면용.")
    @GetMapping("/targets")
    public ApiResponse<java.util.List<TargetResponse>> targets(
            @Parameter(description = "연도", example = "2026") @RequestParam int year) {
        return ApiResponse.success(dashboardService.listTargets(year));
    }
}
