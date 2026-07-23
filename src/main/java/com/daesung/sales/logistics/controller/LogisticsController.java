package com.daesung.sales.logistics.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.LogisMode;
import com.daesung.sales.dsre.gateway.OutboundLogisCost;
import com.daesung.sales.dsre.gateway.PeriodLogisCost;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 물류/작업 · 물류비. DSRE2 의존(단가·수량·인원이 전부 DSRE2에 있음) → daesung.dsre.enabled=true일 때만.
 * 신청(REQ) 단위 출고금액 + 기간 출고·회수(반품/사고) 집계.
 */
@Tag(name = "물류/작업 · 물류비", description = "DSRE2 기반 출고 물류비 계산(자재금액+인원비)")
@RestController
@RequestMapping("/logistics-costs")
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LogisticsController {

    private final DsreGateway dsreGateway;

    @Operation(summary = "출고 물류비 계산(신청 단위)",
            description = "DSRE2에서 자재수량×단가 집계 + 인원함수 호출로 출고 물류비 산출. "
                    + "자재금액(시험지/OMR/단행본·책자/라벨) + 인원비(인원×(BASIC+TRADE)).")
    @GetMapping("/outbound")
    public ApiResponse<OutboundLogisCost> outbound(
            @Parameter(description = "신청번호(REQ_CD)", example = "2001") @RequestParam int reqCd) {
        return ApiResponse.success(dsreGateway.calcOutbound(reqCd));
    }

    @Operation(summary = "기간 출고 물류비 집계",
            description = "신청일(REQ_DATE) 기간의 출고 물류비를 총계로 집계. 자재금액(시험지/OMR/ETC/라벨) + 인원비. "
                    + "mode=ALL(전체)/NORMAL(지사신청 APPLY_GN='S')/ACCIDENT(사고처리 APPLY_GN='A'). "
                    + "includeCancel=false(기본)면 취소(STATE='C') 제외.")
    @GetMapping("/outbound/period")
    public ApiResponse<PeriodLogisCost> outboundPeriod(
            @Parameter(description = "시작일", example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일", example = "2024-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "구분(전체/일반/사고)", example = "ALL")
            @RequestParam(defaultValue = "ALL") LogisMode mode,
            @Parameter(description = "취소분 포함 여부", example = "false")
            @RequestParam(defaultValue = "false") boolean includeCancel) {
        return ApiResponse.success(dsreGateway.calcOutboundPeriod(from, to, mode, includeCancel));
    }

    @Operation(summary = "기간 회수 물류비 집계",
            description = "회수일(REG_DATE) 기간의 회수 물류비를 총계로 집계(자재금액만, 인원비 없음). "
                    + "mode=ALL(전체)/NORMAL(반품 tbl_wol_dtl_b)/ACCIDENT(사고 tbl_wol_dtl). "
                    + "회수 단가는 tbl_logis_cost DTL_CD=0 기준.")
    @GetMapping("/return")
    public ApiResponse<PeriodLogisCost> returnPeriod(
            @Parameter(description = "시작일", example = "2024-01-01")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일", example = "2024-12-31")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "구분(전체/반품/사고)", example = "ALL")
            @RequestParam(defaultValue = "ALL") LogisMode mode) {
        return ApiResponse.success(dsreGateway.calcReturnPeriod(from, to, mode));
    }
}
