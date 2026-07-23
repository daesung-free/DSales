package com.daesung.sales.sale.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.sale.dto.BookInoutResponse;
import com.daesung.sales.sale.dto.CategorySalesResponse;
import com.daesung.sales.sale.dto.NetSalesResponse;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.dto.SalesStatementResponse;
import com.daesung.sales.sale.dto.SalesSummaryResponse;
import com.daesung.sales.sale.dto.TransactionStatementResponse;
import com.daesung.sales.sale.dto.YoyComparisonResponse;
import com.daesung.sales.sale.service.SaleService;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 매출관리. 실제 경로: /api/v1/sales. */
@Tag(name = "매출관리 · 매출", description = "매출 등록/취소/조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;

    @Operation(summary = "통합 매출 조회",
            description = "기간·회계구분·출고유형·거래처로 조회. 기본은 취소건 제외(includeCanceled=true면 포함)")
    @GetMapping
    public ApiResponse<PageResponse<SaleResponse>> list(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "회계구분(SALE/FREE/RETURN)") @RequestParam(required = false) SalesCategory salesCategory,
            @Parameter(description = "출고유형(6종)") @RequestParam(required = false) ShipmentType shipmentType,
            @Parameter(description = "거래처 id") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "취소건 포함 여부(기본 false)") @RequestParam(defaultValue = "false") boolean includeCanceled,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(saleService.search(
                startDate, endDate, salesCategory, shipmentType, partnerId, includeCanceled, pageReq.toPageable()));
    }

    @Operation(summary = "수기 매출 등록(일반)",
            description = "품목별 금액(정가×공급률/100×수량)·세액 산출 + 매출번호(I) 채번. "
                    + "출고유형→회계구분 자동. 위탁출고(CONSIGN_SHIP)는 불가(위탁정산 별도).")
    @PostMapping("/entries")
    public ApiResponse<SalesEntryResponse> createEntries(@Valid @RequestBody SalesEntryRequest req) {
        return ApiResponse.success(saleService.createEntries(req));
    }

    @Operation(summary = "매출 취소(논리 취소)",
            description = "원 매출을 삭제하지 않고 취소 표시 + 원출고 재고 역분개(복구). 이미 취소된 건은 400. "
                    + "위탁정산 매출은 연결 재고이벤트가 없어 재고 불변.")
    @PostMapping("/{id}/cancel")
    public ApiResponse<SaleResponse> cancel(@PathVariable Long id) {
        return ApiResponse.success(saleService.cancel(id));
    }

    @Operation(summary = "순매출 집계 조회",
            description = "기간·거래처로 상품별 매출/증정/교사용/반품 버킷 + 순매출(매출−반품) 집계 + 합계행. "
                    + "취소건 제외. 기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/summary")
    public ApiResponse<SalesSummaryResponse> summary(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id 필터") @RequestParam(required = false) Long partnerId) {
        return ApiResponse.success(saleService.summary(fromDate, toDate, partnerId));
    }

    @Operation(summary = "콘텐츠구분 순매출 조회",
            description = "상품별 순매출(매출−반품). 외부콘텐츠(EXTERNAL)는 매입원가(입고 unit_cost 평균)로 "
                    + "매입액·이익·이익률까지 산출. contentType=SELF(자체교재)/EXTERNAL(외부콘텐츠)/미지정(전체).")
    @GetMapping("/net-summary")
    public ApiResponse<NetSalesResponse> netSummary(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "콘텐츠구분(SELF/EXTERNAL)") @RequestParam(required = false) String contentType) {
        return ApiResponse.success(saleService.netSales(fromDate, toDate, contentType));
    }

    @Operation(summary = "매출액명세서",
            description = "분류코드(catCode) 계층으로 rollup한 매출 명세. 대분류(catCode 첫 글자)→분류→도서 "
                    + "3계층 소계·총계. 금액=공급가, 세액, 합계=금액+세액. 취소건 제외. "
                    + "category=SALE(매출)/FREE(무가)/RETURN(반품)/미지정(전체).")
    @GetMapping("/statement")
    public ApiResponse<SalesStatementResponse> statement(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "회계구분(SALE/FREE/RETURN, 미지정=전체)") @RequestParam(required = false)
            SalesCategory category) {
        return ApiResponse.success(saleService.statement(from, to, category));
    }

    @Operation(summary = "거래명세서",
            description = "거래처×기간의 거래명세서 데이터. 공급자(자사)·공급받는자(거래처 세무정보) + "
                    + "유가(공급가액>0)/무가(교사용·증정) 라인 분리 + 합계(공급가액·세액·합계). 취소 제외. "
                    + "category 미지정=매출(SALE)+무가(FREE), category=RETURN이면 반품명세서.")
    @GetMapping("/transaction-statement")
    public ApiResponse<TransactionStatementResponse> transactionStatement(
            @Parameter(description = "거래처 id", required = true) @RequestParam Long partnerId,
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "회계구분(미지정=매출+무가, RETURN=반품명세서)") @RequestParam(required = false)
            SalesCategory category) {
        return ApiResponse.success(saleService.transactionStatement(partnerId, from, to, category));
    }

    @Operation(summary = "과목별매출현황",
            description = "거래처×분류(catCode)×도서 단위 수량 현황. 매출·반품·순매출(매출−반품)·교사용 수량 + "
                    + "반품률(%). 취소 제외. 거래처·분류 옵션 필터.")
    @GetMapping("/category-summary")
    public ApiResponse<CategorySalesResponse> categorySummary(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "거래처 id 필터(미지정=전체)") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode) {
        return ApiResponse.success(saleService.categorySales(from, to, partnerId, catCode));
    }

    @Operation(summary = "도서입출고현황",
            description = "도서별 매입+매출 이중장부 종합. 매입측(입고·취소·취소율·실매입) + "
                    + "매출측(출고·반품·반품률·실판매) + 정본 재고(종료일 기준) + 매출총이익(실판매−실매입). "
                    + "취소=매입취소(INBOUND 역분개), 반품=매출반품. 취소건 제외. 분류·상품 옵션 필터.")
    @GetMapping("/book-inout")
    public ApiResponse<BookInoutResponse> bookInout(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode,
            @Parameter(description = "상품 id 필터(미지정=전체)") @RequestParam(required = false) Long productId) {
        return ApiResponse.success(saleService.bookInout(from, to, catCode, productId));
    }

    @Operation(summary = "거래처별 매출대비표(전년 동기간)",
            description = "당해 기간 매출을 전년 동기간([from−1년, to−1년])과 비교. 수량·금액 + 증감 + 비율(%, 당해÷전년×100). "
                    + "groupBy=PARTNER(거래처)/CATEGORY(거래처×분류)/BOOK(거래처×도서). SALE만, 취소 제외. 거래처·분류 옵션 필터.")
    @GetMapping("/yoy-comparison")
    public ApiResponse<YoyComparisonResponse> yoyComparison(
            @Parameter(description = "당해 시작일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "당해 종료일(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "집계 단위(PARTNER/CATEGORY/BOOK)") @RequestParam(defaultValue = "PARTNER")
            YoyComparisonResponse.GroupBy groupBy,
            @Parameter(description = "거래처 id 필터(미지정=전체)") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode) {
        return ApiResponse.success(saleService.yoyComparison(from, to, groupBy, partnerId, catCode));
    }
}
