package com.daesung.sales.closing.controller;

import com.daesung.sales.closing.dto.InvoiceAdjustmentResponse;
import com.daesung.sales.closing.dto.RevenueReportResponse;
import com.daesung.sales.closing.dto.TaxFilingResponse;
import com.daesung.sales.closing.dto.TaxInvoiceResponse;
import com.daesung.sales.closing.service.TaxService;
import com.daesung.sales.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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

    @Operation(summary = "계산서 반품/취소 10일 분기 조정 명세",
            description = "특정 월 반품 건을 처리일 기준으로 분류: 매월 10일(발행기준일) 이전=당월 수정발행(AMEND), "
                    + "이후=익월 정산 마이너스(NEXT_MONTH_MINUS). 방식별 합계 포함. 재무팀 확정(2026-07-25). "
                    + "⚠️반영 신고월 세부(원계산서 귀속월)는 발주처 확인 대상. year·month 미지정 시 이번 달.")
    @GetMapping("/invoice-adjustments")
    public ApiResponse<InvoiceAdjustmentResponse> invoiceAdjustments(
            @Parameter(description = "반품 발생 연도(미지정 시 올해)", example = "2026") @RequestParam(required = false) Integer year,
            @Parameter(description = "반품 발생 월 1~12(미지정 시 이번 달)", example = "6") @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ApiResponse.success(taxService.invoiceAdjustments(y, m));
    }

    @Operation(summary = "계산서·세금계산서 월별신고 조회(38p)",
            description = "월(1~12)×발행유형(계산서=면세/세금계산서=과세) 기준 매출·반품·순매출·세액 집계 + 연간 합계. "
                    + "발행유형은 매출 세액(0/≠0)으로 파생. 취소 제외. "
                    + "⚠️'미발행분'은 정의 미확정으로 현재 0. year 미지정 시 올해.")
    @GetMapping("/tax-filing")
    public ApiResponse<TaxFilingResponse> taxFiling(
            @Parameter(description = "신고연도(미지정 시 올해)", example = "2026") @RequestParam(required = false) Integer year) {
        int y = (year != null) ? year : LocalDate.now().getYear();
        return ApiResponse.success(taxService.taxFiling(y));
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

    @Operation(summary = "계산서신고 홈택스 파일(xlsx) 다운로드",
            description = "계산서 데이터를 홈택스 대량발행 양식(면세'05' 시트/과세'01' 시트)으로 xlsx 생성·다운로드. "
                    + "사업자번호·일자는 텍스트(앞자리0 보존), 품목 4개 초과 시 계산서 분할. "
                    + "⚠️ 공급자 실값은 설정 주입, 컬럼 위치는 배포 전 실제 홈택스 템플릿 대조 필요.")
    @GetMapping("/tax-invoices/export")
    public ResponseEntity<byte[]> exportTaxInvoices(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd=작성일자)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id 필터") @RequestParam(required = false) Long partnerId) {
        byte[] xlsx = taxService.exportTaxInvoices(fromDate, toDate, partnerId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"tax-invoices.xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(xlsx);
    }
}

