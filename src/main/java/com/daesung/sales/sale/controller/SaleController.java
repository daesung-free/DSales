package com.daesung.sales.sale.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.sale.dto.BookInoutResponse;
import com.daesung.sales.sale.dto.CategorySalesResponse;
import com.daesung.sales.sale.dto.MonthlyStatementResponse;
import com.daesung.sales.sale.dto.NetSalesResponse;
import com.daesung.sales.sale.dto.ReturnInboundRequest;
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
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
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
    private final ExcelExportUtil excel;

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

    @Operation(summary = "반품입고(29p 물류 진입점)",
            description = "물류가 반품 물량을 입고하면 한 트랜잭션으로 매출 반품(RETURN) 라인 자동 생성 + "
                    + "물류창고 재고 +복구. 담당자는 반품 사실(거래처·도서·수량·공급률)만 입력, 재고 잔량은 자동 산출. "
                    + "재고관리 상품만 재고 복구(모의고사 등 인원기반은 이벤트 없음). 원본 출고번호(선택) 역추적 링크.")
    @PostMapping("/return-inbound")
    public ApiResponse<SalesEntryResponse> returnInbound(@Valid @RequestBody ReturnInboundRequest req) {
        return ApiResponse.success(saleService.returnInbound(req));
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

    @Operation(summary = "콘텐츠구분 순매출 엑셀 다운로드",
            description = "드라이브 순매출조회 형식(상품별 매출·반품·순매출 + 외부콘텐츠 매입·이익·이익률).")
    @GetMapping("/net-summary/export")
    public ResponseEntity<byte[]> netSummaryExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String contentType) {
        List<Col> cols = List.of(
                new Col("상품코드", "productCode"), new Col("상품명", "productName"), new Col("콘텐츠구분", "contentType"),
                new Col("매출수량", "saleQty"), new Col("매출액", "saleAmount"), new Col("무상액", "freeAmount"),
                new Col("반품수량", "returnQty"), new Col("반품액", "returnAmount"),
                new Col("순매출수량", "netQty"), new Col("순매출액", "netAmount"),
                new Col("매입단가", "purchaseUnitCost"), new Col("매입액", "purchaseAmount"),
                new Col("이익", "profit"), new Col("이익률(%)", "marginPct"));
        byte[] xlsx = excel.toXlsx("순매출조회", cols, saleService.netSales(fromDate, toDate, contentType).rows());
        return excel.asDownload(xlsx, "순매출조회.xlsx");
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

    @Operation(summary = "매출액명세서 엑셀 다운로드", description = "드라이브 '매출액정리' 형식(분류/도서별 수량·금액·세액·합계).")
    @GetMapping("/statement/export")
    public ResponseEntity<byte[]> statementExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SalesCategory category) {
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"), new Col("수량", "qty"),
                new Col("금액", "amount"), new Col("세액", "tax"), new Col("합계", "total"));
        byte[] xlsx = excel.toXlsx("매출액명세서", cols, saleService.statement(from, to, category).rows());
        return excel.asDownload(xlsx, "매출액명세서_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "월별매출액명세서(37p)",
            description = "구분(대분류=catCode 첫 글자)×상품별 성적처리/비처리 인원·금액 + 계 + 과세매출액 + 부가세. "
                    + "대분류 소계·총계 포함. 매출(SALE)만 집계(무상·반품 제외), 취소 제외. "
                    + "인원=수량(모의고사=응시인원), 성적처리=매출등록 proc_type(GRADED, 미지정=비처리). "
                    + "year·month 미지정 시 이번 달.")
    @GetMapping("/monthly-statement")
    public ApiResponse<MonthlyStatementResponse> monthlyStatement(
            @Parameter(description = "연도(미지정 시 올해)", example = "2026") @RequestParam(required = false) Integer year,
            @Parameter(description = "월 1~12(미지정 시 이번 달)", example = "6") @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        return ApiResponse.success(saleService.monthlyStatement(y, m));
    }

    @Operation(summary = "월별매출액명세서(37p) 엑셀 다운로드",
            description = "드라이브 '연구소 월별매출액명세서' 형식(구분×성적처리/비처리 인원·금액+과세·부가세).")
    @GetMapping("/monthly-statement/export")
    public ResponseEntity<byte[]> monthlyStatementExport(
            @RequestParam(required = false) Integer year, @RequestParam(required = false) Integer month) {
        LocalDate now = LocalDate.now();
        int y = (year != null) ? year : now.getYear();
        int m = (month != null) ? month : now.getMonthValue();
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("대분류", "majorCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"),
                new Col("성적처리인원", "gradedQty"), new Col("성적처리금액", "gradedAmount"),
                new Col("비처리인원", "ungradedQty"), new Col("비처리금액", "ungradedAmount"),
                new Col("계인원", "totalQty"), new Col("계금액", "totalAmount"),
                new Col("과세매출액", "taxableAmount"), new Col("부가세", "vat"));
        byte[] xlsx = excel.toXlsx("월별매출액명세서", cols, saleService.monthlyStatement(y, m).rows());
        return excel.asDownload(xlsx, "월별매출액명세서_" + y + "-" + String.format("%02d", m) + ".xlsx");
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

    @Operation(summary = "과목별매출현황 엑셀 다운로드", description = "거래처×분류×도서 매출/반품/순매출/교사용 수량+반품률.")
    @GetMapping("/category-summary/export")
    public ResponseEntity<byte[]> categorySummaryExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String catCode) {
        List<Col> cols = List.of(
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"),
                new Col("매출수량", "saleQty"), new Col("반품수량", "returnQty"), new Col("순매출수량", "netQty"),
                new Col("교사용수량", "teacherQty"), new Col("반품률(%)", "returnRate"));
        byte[] xlsx = excel.toXlsx("과목별매출현황", cols, saleService.categorySales(from, to, partnerId, catCode).rows());
        return excel.asDownload(xlsx, "과목별매출현황_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "도서입출고현황 엑셀 다운로드", description = "도서별 매입+매출 이중장부 + 정본재고 + 매출총이익.")
    @GetMapping("/book-inout/export")
    public ResponseEntity<byte[]> bookInoutExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String catCode, @RequestParam(required = false) Long productId) {
        List<Col> cols = List.of(
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"), new Col("정가", "listPrice"),
                new Col("입고수량", "inboundQty"), new Col("입고금액", "inboundAmount"),
                new Col("취소수량", "cancelQty"), new Col("취소금액", "cancelAmount"), new Col("취소율(%)", "cancelRate"),
                new Col("실매입수량", "netPurchaseQty"), new Col("실매입금액", "netPurchaseAmount"),
                new Col("출고수량", "outboundQty"), new Col("출고금액", "outboundAmount"),
                new Col("반품수량", "returnQty"), new Col("반품금액", "returnAmount"), new Col("반품률(%)", "returnRate"),
                new Col("실판매수량", "netSalesQty"), new Col("실판매금액", "netSalesAmount"),
                new Col("재고", "stockQty"), new Col("매출총이익", "grossMargin"));
        byte[] xlsx = excel.toXlsx("도서입출고현황", cols, saleService.bookInout(from, to, catCode, productId).rows());
        return excel.asDownload(xlsx, "도서입출고현황_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "거래처별 매출대비표 엑셀 다운로드", description = "당해 vs 전년 동기간 수량·금액 증감·비율.")
    @GetMapping("/yoy-comparison/export")
    public ResponseEntity<byte[]> yoyComparisonExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "PARTNER") YoyComparisonResponse.GroupBy groupBy,
            @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String catCode) {
        List<Col> cols = List.of(
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"),
                new Col("당해수량", "curQty"), new Col("당해금액", "curAmount"),
                new Col("전년수량", "prevQty"), new Col("전년금액", "prevAmount"),
                new Col("증감수량", "diffQty"), new Col("증감금액", "diffAmount"),
                new Col("수량비율(%)", "qtyRatioPct"), new Col("금액비율(%)", "amountRatioPct"));
        byte[] xlsx = excel.toXlsx("매출대비표", cols,
                saleService.yoyComparison(from, to, groupBy, partnerId, catCode).rows());
        return excel.asDownload(xlsx, "매출대비표_" + from + "_" + to + ".xlsx");
    }
}
