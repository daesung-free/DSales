package com.daesung.sales.sale.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.sale.dto.BulkImportResponse;
import com.daesung.sales.sale.service.BulkSalesImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매출관리 · 매출일괄등록(DSRE 교재). DSRE2 의존 → daesung.dsre.enabled=true일 때만.
 * 미리보기(dryRun) 후 등록 권장. 등록은 매출 생성 + DSRE state='T' write-back(멱등).
 */
@Tag(name = "매출관리 · 매출일괄등록", description = "DSRE2 미처리 주문을 매출로 일괄 등록(+write-back). 멱등")
@RestController
@RequestMapping("/sales/bulk")
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class BulkSalesController {

    private final BulkSalesImportService bulkSalesImportService;

    @Operation(summary = "매출일괄등록 미리보기(dryRun)",
            description = "DSRE2 미처리(state='A') 대상을 조회만 — 매출 생성·write-back 없음. 대상 확인용.")
    @GetMapping("/preview")
    public ApiResponse<BulkImportResponse> preview(
            @Parameter(description = "신청일 시작(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "신청일 종료(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(bulkSalesImportService.importSales(fromDate, toDate, true));
    }

    @Operation(summary = "매출일괄등록 실행",
            description = "DSRE2 미처리 주문을 매출로 생성 + DSRE state='T' write-back. "
                    + "소스키 멱등(중복 방지) — 재실행해도 이미 등록분은 스킵.")
    @PostMapping("/import")
    public ApiResponse<BulkImportResponse> importSales(
            @Parameter(description = "신청일 시작(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "신청일 종료(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(bulkSalesImportService.importSales(fromDate, toDate, false));
    }
}
