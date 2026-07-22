package com.daesung.sales.closing.controller;

import com.daesung.sales.closing.dto.RevenueReportResponse;
import com.daesung.sales.closing.dto.TaxInvoiceResponse;
import com.daesung.sales.closing.service.TaxService;
import com.daesung.sales.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 마감관리 · 세무. 실제 경로: /api/v1/closing. 스펙 §마감관리(revenue-report). */
@Tag(name = "마감관리 · 세무", description = "수익신고(거래처×월 순매출/세액). 계산서신고(홈택스)는 후속")
@RestController
@RequiredArgsConstructor
@RequestMapping("/closing")
public class TaxController {

    private final TaxService taxService;

    @Operation(summary = "수익신고 조회",
            description = "거래처×월 순매출(공급가)/세액 집계 + 거래처 소계 + 전체 합계. 취소 제외, 반품 차감. "
                    + "taxType=FREE(면세)/TAXABLE(과세)/미지정(전체). 기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/revenue-report")
    public ApiResponse<RevenueReportResponse> revenueReport(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "과세구분(FREE=면세/TAXABLE=과세/미지정=전체)") @RequestParam(required = false) String taxType) {
        return ApiResponse.success(taxService.revenueReport(fromDate, toDate, taxType));
    }

    @Operation(summary = "계산서신고 데이터 조회",
            description = "거래처×과세구분 단위 계산서(품목=도서별) 목록. 면세'05'/과세'01', 공급가액·세액·합계검증. "
                    + "공급자(자사)는 설정 주입. 홈택스 파일 export는 후속 엔드포인트. 기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/tax-invoices")
    public ApiResponse<TaxInvoiceResponse> taxInvoices(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd=작성일자, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id 필터") @RequestParam(required = false) Long partnerId) {
        return ApiResponse.success(taxService.taxInvoices(fromDate, toDate, partnerId));
    }
}
