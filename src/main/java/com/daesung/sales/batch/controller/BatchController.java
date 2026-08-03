package com.daesung.sales.batch.controller;

import com.daesung.sales.batch.dto.BatchRunResponse;
import com.daesung.sales.batch.repository.BatchJobRunRepository;
import com.daesung.sales.batch.service.CollateralExpiryBatch;
import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 배치잡 실행·이력. 실제 경로: /api/v1/batch. */
@Tag(name = "배치", description = "배치잡 수동 실행 및 실행 이력(개발문서 19.0)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/batch")
public class BatchController {

    private final CollateralExpiryBatch collateralExpiryBatch;
    private final BatchJobRunRepository runRepository;

    @Operation(summary = "담보만기 알림 배치 수동 실행",
            description = """
                    담보 만기 1개월 전(기준일+30일) 거래처를 찾아 알림을 적재한다(25p).
                    평소에는 매일 03시 자동 실행되며, 이 API는 재실행·검수 시연용이다.
                    거래처×만기일 단위로 중복을 막아 **몇 번 실행해도 알림이 늘지 않는다**.
                    실패 시 최대 3회까지 재시도하고 매 시도가 이력에 남는다.""")
    @PostMapping("/jobs/collateral-expiry/run")
    public ApiResponse<BatchRunResponse> runCollateralExpiry(
            @Parameter(description = "기준일(미지정 시 오늘)")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate) {
        LocalDate base = (baseDate == null) ? LocalDate.now() : baseDate;
        return ApiResponse.success(BatchRunResponse.from(collateralExpiryBatch.run(base)));
    }

    @Operation(summary = "배치 실행 이력 조회",
            description = "최신순. jobName으로 특정 잡만 필터. 성공/실패와 시도 횟수가 남는다.")
    @GetMapping("/runs")
    public ApiResponse<PageResponse<BatchRunResponse>> runs(
            @Parameter(description = "잡 이름(예: COLLATERAL_EXPIRY). 미지정 시 전체")
            @RequestParam(required = false) String jobName,
            @ParameterObject PageRequestDto pageReq) {
        var page = (jobName == null || jobName.isBlank())
                ? runRepository.findAllByOrderByIdDesc(pageReq.toPageable())
                : runRepository.findByJobNameOrderByIdDesc(jobName, pageReq.toPageable());
        return ApiResponse.success(PageResponse.of(page.map(BatchRunResponse::from)));
    }
}
