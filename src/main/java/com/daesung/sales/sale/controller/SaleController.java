package com.daesung.sales.sale.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.sale.dto.BookInoutResponse;
import com.daesung.sales.sale.dto.CategorySalesResponse;
import com.daesung.sales.sale.dto.MonthlyStatementResponse;
import com.daesung.sales.sale.dto.NetSalesResponse;
import com.daesung.sales.sale.dto.ReturnInboundRequest;
import com.daesung.sales.sale.dto.ReturnableResponse;
import com.daesung.sales.sale.dto.RoundWorkStatusRow;
import com.daesung.sales.sale.dto.AttendancePeriodResponse;
import com.daesung.sales.sale.dto.AttendanceResponse;
import com.daesung.sales.sale.dto.SaleResponse;
import com.daesung.sales.sale.dto.SalesEntryRequest;
import com.daesung.sales.sale.dto.SalesEntryResponse;
import com.daesung.sales.sale.dto.SalesStatementResponse;
import com.daesung.sales.sale.dto.SalesUploadResponse;
import com.daesung.sales.sale.dto.SalesSummaryResponse;
import com.daesung.sales.sale.dto.TransactionStatementResponse;
import com.daesung.sales.sale.dto.YoyComparisonResponse;
import com.daesung.sales.sale.service.SaleReportService;
import com.daesung.sales.sale.service.SaleService;
import com.daesung.sales.sale.service.SalesUploadService;
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
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 매출관리. 실제 경로: /api/v1/sales. */
@Tag(name = "매출관리 · 매출", description = "매출 등록/취소/조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/sales")
public class SaleController {

    private final SaleService saleService;
    private final SaleReportService saleReportService;
    private final com.daesung.sales.sale.service.AttendanceService attendanceService;
    private final SalesUploadService salesUploadService;
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

    @Operation(summary = "매출 엑셀 업로드(표준양식 일괄등록)",
            description = "표준양식 엑셀(12컬럼: 거래일자·거래처코드·학교코드·분류코드·도서코드·회차·정가·공급률·수량·금액·구분·메모)을 "
                    + "매출 원장으로 일괄 등록. 상품=분류+도서 조합, 정가·공급률 미입력 시 도서·거래처단가 자동조회, 공급률 0.75는 75로 정규화. "
                    + "정상출고/증정용/교사용만 허용(위탁·취소·반품은 거부). 재고는 미반영(레거시 매출가져오기와 동일). 행별 결과 반환.")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<SalesUploadResponse> upload(
            @Parameter(description = "표준양식 xlsx 파일", required = true) @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(salesUploadService.upload(file));
    }

    @Operation(summary = "반품 가능내역 조회(교재식 반품)",
            description = "거래처(옵션 도서)의 도서×정가×공급률별 반품가능수량(누적 판매출고 − 기반품, >0만). "
                    + "반품입고 시 이 목록에서 라인을 골라 그 범위 내에서만 반품하며, 정가·공급률은 원 출고건을 그대로 사용.")
    @GetMapping("/returnable")
    public ApiResponse<ReturnableResponse> returnable(
            @Parameter(description = "거래처 id", required = true) @RequestParam Long partnerId,
            @Parameter(description = "도서(상품) id 필터(옵션)") @RequestParam(required = false) Long productId) {
        return ApiResponse.success(saleService.returnable(partnerId, productId));
    }

    @Operation(summary = "반품입고(29p 물류 진입점, 교재식)",
            description = "물류가 반품 물량을 입고하면 한 트랜잭션으로 매출 반품(RETURN) 라인 자동 생성 + 물류창고 재고 +복구. "
                    + "교재식: 반품수량은 (누적 판매출고 − 기반품) 범위 내여야 하며(초과 시 409), 정가·공급률은 원 출고건과 일치해야 함. "
                    + "재고관리 상품만 재고 복구(모의고사 등 인원기반은 이벤트 없음). 원본 출고번호(선택) 역추적 링크. "
                    + "반품 가능내역은 GET /sales/returnable로 먼저 조회.")
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
        return ApiResponse.success(saleReportService.summary(fromDate, toDate, partnerId));
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
        return ApiResponse.success(saleReportService.netSales(fromDate, toDate, contentType));
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
        byte[] xlsx = excel.toXlsx("순매출조회", cols, saleReportService.netSales(fromDate, toDate, contentType).rows());
        return excel.asDownload(xlsx, "순매출조회.xlsx");
    }

    @Operation(summary = "매출액명세서",
            description = "대분류→분류(catCode)→도서 3계층으로 rollup한 매출 명세. 소계·총계 포함. "
                    + "★대분류는 상품의 세부구분에서 파생된다(모의고사·교재·기타고사·특강·기타). "
                    + "세부구분이 없는 상품은 '미분류'로 모인다 — 집계에서 빼지 않는다. 금액=공급가, 세액, 합계=금액+세액. 취소건 제외. "
                    + "category=SALE(매출)/FREE(무가)/RETURN(반품)/미지정(전체).")
    @GetMapping("/statement")
    public ApiResponse<SalesStatementResponse> statement(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "회계구분(SALE/FREE/RETURN, 미지정=전체)") @RequestParam(required = false)
            SalesCategory category) {
        return ApiResponse.success(saleReportService.statement(from, to, category));
    }

    @Operation(summary = "매출액명세서 엑셀 다운로드", description = "드라이브 '매출액정리' 형식(분류/도서별 수량·금액·세액·합계).")
    @GetMapping("/statement/export")
    public ResponseEntity<byte[]> statementExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SalesCategory category) {
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("대분류", "majorName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"), new Col("수량", "qty"),
                new Col("금액", "amount"), new Col("세액", "tax"), new Col("합계", "total"));
        byte[] xlsx = excel.toXlsx("매출액명세서", cols, saleReportService.statement(from, to, category).rows());
        return excel.asDownload(xlsx, "매출액명세서_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "월별매출액명세서(37p)",
            description = "구분(대분류)×상품별 성적처리/비처리 인원·금액 + 계 + 과세매출액 + 부가세. "
                    + "★대분류는 상품의 세부구분에서 파생(모의고사·교재·기타고사·특강·기타). "
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
        return ApiResponse.success(saleReportService.monthlyStatement(y, m));
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
                new Col("구분", "rowType"), new Col("대분류", "majorName"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"),
                new Col("성적처리인원", "gradedQty"), new Col("성적처리금액", "gradedAmount"),
                new Col("비처리인원", "ungradedQty"), new Col("비처리금액", "ungradedAmount"),
                new Col("계인원", "totalQty"), new Col("계금액", "totalAmount"),
                new Col("과세매출액", "taxableAmount"), new Col("부가세", "vat"));
        byte[] xlsx = excel.toXlsx("월별매출액명세서", cols, saleReportService.monthlyStatement(y, m).rows());
        return excel.asDownload(xlsx, "월별매출액명세서_" + y + "-" + String.format("%02d", m) + ".xlsx");
    }

    @Operation(summary = "거래명세서",
            description = "거래처×기간의 거래명세서 데이터. 공급자(자사)·공급받는자(거래처 세무정보) + "
                    + "유가(공급가액>0)/무가(교사용·증정) 라인 분리 + 합계(공급가액·세액·합계). 취소 제외. "
                    + "category 미지정=매출(SALE)+무가(FREE), category=RETURN이면 반품명세서.")
    @GetMapping("/transaction-statement")
    public ApiResponse<TransactionStatementResponse> transactionStatement(
            @Parameter(description = "거래처 id", required = true) @RequestParam Long partnerId,
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "회계구분(미지정=매출+무가, RETURN=반품명세서)") @RequestParam(required = false)
            SalesCategory category) {
        return ApiResponse.success(saleReportService.transactionStatement(partnerId, from, to, category));
    }

    @Operation(summary = "거래명세서 엑셀 다운로드", description = "유가+무가 라인 통합(품명·정가·공급률·단가·수량·금액·세액).")
    @GetMapping("/transaction-statement/export")
    public ResponseEntity<byte[]> transactionStatementExport(
            @RequestParam Long partnerId,
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) SalesCategory category) {
        var d = saleReportService.transactionStatement(partnerId, from, to, category);
        var all = new java.util.ArrayList<>(d.pricedLines());
        all.addAll(d.freeLines());
        List<Col> cols = List.of(
                new Col("순번", "seq"), new Col("품명", "bookLabel"), new Col("도서코드", "bookCode"),
                new Col("수량", "qty"), new Col("정가", "listPrice"), new Col("공급률", "supplyRate"),
                new Col("단가", "unitSupplyPrice"), new Col("공급가액", "supplyAmount"), new Col("세액", "tax"),
                new Col("구분", "category"), new Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("거래명세서", cols, all);
        return excel.asDownload(xlsx, "거래명세서_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "과목별매출현황",
            description = "거래처×분류(catCode)×도서 단위 수량 현황. 매출·반품·순매출(매출−반품)·교사용 수량 + "
                    + "반품률(%). 취소 제외. 거래처·분류 옵션 필터.")
    @GetMapping("/category-summary")
    public ApiResponse<CategorySalesResponse> categorySummary(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "거래처 id 필터(미지정=전체)") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode) {
        return ApiResponse.success(saleReportService.categorySales(from, to, partnerId, catCode));
    }

    @Operation(summary = "도서입출고현황",
            description = "도서별 매입+매출 이중장부 종합. 매입측(입고·취소·취소율·실매입) + "
                    + "매출측(출고·반품·반품률·실판매) + 정본 재고(종료일 기준) + 매출총이익(실판매−실매입). "
                    + "취소=매입취소(INBOUND 역분개), 반품=매출반품. 취소건 제외. 분류·상품 옵션 필터.")
    @GetMapping("/book-inout")
    public ApiResponse<BookInoutResponse> bookInout(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode,
            @Parameter(description = "상품 id 필터(미지정=전체)") @RequestParam(required = false) Long productId) {
        return ApiResponse.success(saleReportService.bookInout(from, to, catCode, productId));
    }

    @Operation(summary = "거래처별 매출대비표(전년 동기간)",
            description = "당해 기간 매출을 전년 동기간([from−1년, to−1년])과 비교. 수량·금액 + 증감 + 비율(%, 당해÷전년×100). "
                    + "groupBy=PARTNER(거래처)/CATEGORY(거래처×분류)/BOOK(거래처×도서). SALE만, 취소 제외. 거래처·분류 옵션 필터.")
    @GetMapping("/yoy-comparison")
    public ApiResponse<YoyComparisonResponse> yoyComparison(
            @Parameter(description = "당해 시작일(yyyy-MM-dd)", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @Parameter(description = "당해 종료일(yyyy-MM-dd)", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @Parameter(description = "집계 단위(PARTNER/CATEGORY/BOOK)") @RequestParam(defaultValue = "PARTNER")
            YoyComparisonResponse.GroupBy groupBy,
            @Parameter(description = "거래처 id 필터(미지정=전체)") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode) {
        return ApiResponse.success(saleReportService.yoyComparison(from, to, groupBy, partnerId, catCode));
    }

    @Operation(summary = "과목별매출현황 엑셀 다운로드", description = "거래처×분류×도서 매출/반품/순매출/교사용 수량+반품률.")
    @GetMapping("/category-summary/export")
    public ResponseEntity<byte[]> categorySummaryExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) Long partnerId, @RequestParam(required = false) String catCode) {
        List<Col> cols = List.of(
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"),
                new Col("매출수량", "saleQty"), new Col("반품수량", "returnQty"), new Col("순매출수량", "netQty"),
                new Col("교사용수량", "teacherQty"), new Col("반품률(%)", "returnRate"));
        byte[] xlsx = excel.toXlsx("과목별매출현황", cols, saleReportService.categorySales(from, to, partnerId, catCode).rows());
        return excel.asDownload(xlsx, "과목별매출현황_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "도서입출고현황 엑셀 다운로드", description = "도서별 매입+매출 이중장부 + 정본재고 + 매출총이익.")
    @GetMapping("/book-inout/export")
    public ResponseEntity<byte[]> bookInoutExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
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
        byte[] xlsx = excel.toXlsx("도서입출고현황", cols, saleReportService.bookInout(from, to, catCode, productId).rows());
        return excel.asDownload(xlsx, "도서입출고현황_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "거래처별 매출대비표 엑셀 다운로드", description = "당해 vs 전년 동기간 수량·금액 증감·비율.")
    @GetMapping("/yoy-comparison/export")
    public ResponseEntity<byte[]> yoyComparisonExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
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
                saleReportService.yoyComparison(from, to, groupBy, partnerId, catCode).rows());
        return excel.asDownload(xlsx, "매출대비표_" + from + "_" + to + ".xlsx");
    }

    @Operation(summary = "회차별 작업현황(구 IC회차별작업현황)",
            description = """
                    분류×도서×**회차**를 행으로, 포장구분(개별1/개별2/반별) 수량을 열로 펼쳐 보여준다.
                    레거시 IC회차별작업현황 화면 재현 — 회차가 없는 건(0)은 제외되고, 취소 건도 빠진다.
                    catCode를 주면 해당 분류만 조회한다.""")
    @GetMapping("/round-work-status")
    public ApiResponse<List<RoundWorkStatusRow>> roundWorkStatus(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "분류코드(선택)") @RequestParam(required = false) String catCode) {
        return ApiResponse.success(saleReportService.roundWorkStatus(fromDate, toDate, catCode));
    }

    @Operation(summary = "응시현황(연도별)",
            description = """
                    거래처별 **월별 수량·매출 크로스탭**(1월[수량]·1월[매출] … 12월 + 합계).

                    ⚠️이름과 달리 '시험 신청·응시율' 표가 아니다 — 레거시 응시현황.vb 실물이
                    이 구성이고, 신청·응시율 같은 항목은 레거시 어디에도 없다.

                    · 행은 **거래처 → 지역구분 소계 → 총계** 3단이다(레거시 rollup과 같다).
                    · 반품은 음수로 반영되고 취소는 제외된다(레거시가 반품을 음수로 저장해
                      SUM만으로 순수량이 나오던 것과 값이 같아진다).
                    · 학년·상품구분 필터는 레거시와 같은 축이다(도서의 학년/상품구분).

                    발주처 회신(2-2): 레거시 화면이 "2022년까지만 조회 가능"으로 막혀 있고
                    전산담당자가 부재라, 매출 데이터로 다시 만들어 달라는 요청이었다.""")
    @GetMapping("/attendance-yearly")
    public ApiResponse<AttendanceResponse> attendanceYearly(
            @Parameter(description = "조회 연도", example = "2026") @RequestParam int year,
            @Parameter(description = "학년 필터(도서 학년). 미지정=전체", example = "3")
            @RequestParam(required = false) String grade,
            @Parameter(description = "상품구분 필터. 미지정=전체", example = "교재")
            @RequestParam(required = false) String productType) {
        return ApiResponse.success(attendanceService.yearly(year, grade, productType));
    }

    @Operation(summary = "응시현황(연도별) 엑셀 다운로드")
    @GetMapping("/attendance-yearly/export")
    public org.springframework.http.ResponseEntity<byte[]> attendanceYearlyExport(
            @RequestParam int year,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) String productType) {
        var cols = new java.util.ArrayList<com.daesung.sales.common.excel.ExcelExportUtil.Col>();
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("구분", "rowType"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("지역구분", "regionGroup"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("특약점코드", "partnerCode"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("특약점명", "partnerName"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("지역", "cityName"));
        for (int m = 1; m <= 12; m++) {
            cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col(
                    m + "월[수량]", "monthlyQty[" + (m - 1) + "]"));
            cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col(
                    m + "월[매출]", "monthlyAmount[" + (m - 1) + "]"));
        }
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("합계[수량]", "totalQty"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("합계[매출]", "totalAmount"));
        byte[] xlsx = excel.toXlsx("응시현황", cols, attendanceService.yearly(year, grade, productType).rows());
        return excel.asDownload(xlsx, "응시현황_연도별.xlsx");
    }

    @Operation(summary = "응시현황(기간별) 18p",
            description = """
                    모의고사 응시(처리)현황을 **기간별**로 본다.
                    행은 지역 · 특약점 · 학교 · 학년, 열은 **조회 기간의 각 월** × 처리/비처리/계.
                    소계는 학교 → 특약점 → 지역 → 총계 순으로 붙는다.

                    · **모의고사만** 집계한다. 판별은 상품의 **대분류**로 한다.
                    · 처리/비처리는 매출등록의 성적처리 구분(`procType`)이다(미지정=비처리).
                    · 인원 = 매출 수량. 매출(SALE)만 — 증정·반품은 응시가 아니다.
                    · 월 컬럼 개수는 **조회 기간에 따라 달라진다**. 응답 `months`(yyyy-MM)가
                      `monthlyGraded`/`monthlyUngraded`/`monthlyTotal` 배열의 순서를 알려준다.

                    ⚠️레거시(`고사별처리인원.vb`)는 이 화면이 **"2022년까지만 조회 가능"** 으로 막혀 있었다.
                    정본에는 '2022년 이후 데이터 미유입'으로 적혀 있으나 실측 결과 원인은 데이터가 아니라
                    **코드**였다 — 모의고사 판별을 연도가 박힌 분류코드(`catCode in ('M22A','M22B')`)로 하고
                    영역·월을 도서명 문자열로 긁어서, 연도마다 SQL을 복붙해야 했고 2022년에 멈춘 것이다.
                    여기서는 연도에 의존하는 부분을 두지 않아 해가 바뀌어도 손댈 곳이 없다.""")
    @GetMapping("/attendance-period")
    public ApiResponse<AttendancePeriodResponse> attendancePeriod(
            @Parameter(description = "시작일(yyyy-MM-dd)", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "학년 필터(도서 학년). 미지정=전체", example = "고3")
            @RequestParam(required = false) String grade,
            @Parameter(description = "거래처(특약점) 필터. 미지정=전체")
            @RequestParam(required = false) Long partnerId) {
        return ApiResponse.success(attendanceService.period(fromDate, toDate, grade, partnerId));
    }

    @Operation(summary = "응시현황(기간별) 엑셀 다운로드",
            description = "월 컬럼은 조회 기간에서 만들어지므로 기간에 따라 열 개수가 달라진다.")
    @GetMapping("/attendance-period/export")
    public org.springframework.http.ResponseEntity<byte[]> attendancePeriodExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String grade,
            @RequestParam(required = false) Long partnerId) {
        AttendancePeriodResponse res = attendanceService.period(fromDate, toDate, grade, partnerId);
        var cols = new java.util.ArrayList<com.daesung.sales.common.excel.ExcelExportUtil.Col>();
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("구분", "rowType"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("지역", "region"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("특약점코드", "partnerCode"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("특약점명", "partnerName"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("학교코드", "schoolCode"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("학교명", "schoolName"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("학년", "grade"));
        // 월 컬럼은 응답이 알려준 목록으로 만든다 — 상수로 두면 레거시와 같은 하드코딩이 된다.
        for (int i = 0; i < res.months().size(); i++) {
            String m = res.months().get(i);
            cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col(
                    m + "[처리]", "monthlyGraded[" + i + "]"));
            cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col(
                    m + "[비처리]", "monthlyUngraded[" + i + "]"));
            cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col(
                    m + "[계]", "monthlyTotal[" + i + "]"));
        }
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("합계[처리]", "gradedTotal"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("합계[비처리]", "ungradedTotal"));
        cols.add(new com.daesung.sales.common.excel.ExcelExportUtil.Col("합계[계]", "total"));
        byte[] xlsx = excel.toXlsx("응시현황(기간별)", cols, res.rows());
        return excel.asDownload(xlsx, "응시현황_기간별_" + fromDate + "_" + toDate + ".xlsx");
    }
}
