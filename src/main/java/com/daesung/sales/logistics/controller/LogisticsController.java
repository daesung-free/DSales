package com.daesung.sales.logistics.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.OutboundLogisCost;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 물류/작업 · 물류비. DSRE2 의존(단가·수량·인원이 전부 DSRE2에 있음) → daesung.dsre.enabled=true일 때만.
 * MVP: 신청(REQ) 단위 출고금액. 기간·회수(반품) 계산은 후속.
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
}
