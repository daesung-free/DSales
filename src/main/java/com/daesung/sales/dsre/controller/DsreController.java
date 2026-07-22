package com.daesung.sales.dsre.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** DSRE2 연동 진단(1단계 검증용). daesung.dsre.enabled=true일 때만. 물류비 등은 게이트웨이를 내부 사용. */
@Tag(name = "DSRE2 연동", description = "DSRE2(기존 라이브 유지) 게이트웨이 진단")
@RestController
@RequestMapping("/dsre")
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DsreController {

    private final DsreGateway dsreGateway;

    @Operation(summary = "신청인원 조회(FUNC_REQINWON_GET)",
            description = "DSRE2 저장함수를 호출해 신청 REQ_CD의 인원을 산출. 물류비 인원기준 계산의 기반.")
    @GetMapping("/req-inwon")
    public ApiResponse<Integer> reqInwon(
            @Parameter(description = "신청번호(REQ_CD)", example = "1001") @RequestParam int reqCd) {
        return ApiResponse.success(dsreGateway.reqInwon(reqCd));
    }
}
