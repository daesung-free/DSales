package com.daesung.sales.sale.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.query.Keywords;
import com.daesung.sales.common.query.MultiSelect;
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
import com.daesung.sales.sale.dto.StatementKind;
import com.daesung.sales.sale.entity.SalesType;
import com.daesung.sales.sale.dto.SalesUploadResponse;
import com.daesung.sales.sale.dto.SalesSummaryResponse;
import com.daesung.sales.sale.dto.TransactionStatementResponse;
import com.daesung.sales.sale.dto.YoyComparisonResponse;
import com.daesung.sales.sale.service.SaleReportService;
import com.daesung.sales.sale.service.SaleService;
import com.daesung.sales.sale.service.SalesUploadService;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.salestype.entity.TradeClass;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.excel.ExcelExportUtil.Heading;
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

    /**
     * 엑셀 한 장에 담는 최대 행수. 조회 화면의 페이지 크기(200)와 무관하게 전량을 담기 위한 상한이다.
     * ★무제한으로 두지 않는 이유: 기간을 안 주고 내려받으면 전 기간이 메모리에 한 번에 올라온다.
     *   넘치면 잘라 내는 것이 아니라 <b>기간을 좁히라고 알려주는</b> 편이 낫다(조용한 누락 방지).
     */
    private static final int EXPORT_MAX = 50_000;

    @Operation(summary = "통합 매출 조회(7p 출고/반품조회 · 12p 통합매출조회)",
            description = """
                    기간·거래처와 **표준 구분값 축**으로 조회한다. 기본은 취소건 제외.

                    발주처 확정 4축(2026-08-05)과 파라미터 대응 —
                    | 축 | 값 | 파라미터 |
                    |---|---|---|
                    | 거래분류(5) | 매출/무상/반품/입고/폐기 | `tradeClass` |
                    | 구분(상세)(3) | 매출/무상/반품 | `salesCategory` |
                    | 출고유형(6) | 정상출고/위탁출고/증정용/교사용/반품/취소 | `shipmentType` |
                    | 창고(2) | 물류창고/위탁창고 | 응답 `warehouseName` |

                    · 매출 원장의 거래분류는 **구분(상세)에서 파생**된다 — 따로 저장하지 않아 둘이 어긋날 수 없다.

                    ### 다중선택(체크박스) — 2026-08-31 발주처 요구
                    네 축 모두 **복수 파라미터**를 받는다. 값을 콤마로 잇거나 파라미터를 반복하면 된다
                    (`tradeClasses=FREE,RETURN` 또는 `tradeClasses=FREE&tradeClasses=RETURN`).

                    · **단수 파라미터는 그대로 살아 있고**, 복수와 같이 오면 **합집합**이다.
                      단수는 '원소가 하나인 다중선택'으로 본다.
                    · `tradeClasses`에 **입고·폐기가 섞이면 그 값만 무시**한다 — 재고 원장의
                      거래라 매출에는 없다. '무상+입고'를 고르면 무상은 정상 조회된다.
                      단 **입고·폐기만** 골랐다면 남는 조건이 없어 **빈 결과**다.
                    · `tradeClasses`와 `salesCategories`를 같이 주면 **교집합**이다(별개 축이라 둘 다 만족).""")
    @GetMapping
    public ApiResponse<PageResponse<SaleResponse>> list(
            @Parameter(description = "시작일(yyyy-MM-dd). `fromDate`로 보내도 된다")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일(yyyy-MM-dd). `toDate`로 보내도 된다")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "시작일 별칭 — 다른 조회 API가 쓰는 이름")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일 별칭 — 다른 조회 API가 쓰는 이름")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "구분(상세) — 회계구분 SALE/FREE/RETURN (단건. 다중은 salesCategories)")
            @RequestParam(required = false) SalesCategory salesCategory,
            @Parameter(description = "구분(상세) **다중선택** — 예: SALE,RETURN")
            @RequestParam(required = false) List<SalesCategory> salesCategories,
            @Parameter(description = "거래분류 SALES(매출)/FREE(무상)/RETURN(반품)/INBOUND(입고)/DISPOSE(폐기). "
                    + "입고·폐기는 재고 원장 거래라 매출 조회에서는 무시된다 (단건. 다중은 tradeClasses)")
            @RequestParam(required = false) TradeClass tradeClass,
            @Parameter(description = "거래분류 **다중선택** — 예: FREE,RETURN (화면3 체크박스)")
            @RequestParam(required = false) List<TradeClass> tradeClasses,
            @Parameter(description = "출고유형(6종) (단건. 다중은 shipmentTypes)")
            @RequestParam(required = false) ShipmentType shipmentType,
            @Parameter(description = "출고유형 **다중선택** — 예: NORMAL_SHIP,GIFT")
            @RequestParam(required = false) List<ShipmentType> shipmentTypes,
            @Parameter(description = "거래처 id (단건. 다중은 partnerIds)")
            @RequestParam(required = false) Long partnerId,
            @Parameter(description = "거래처 id **다중선택** — 좌측 트리뷰 체크박스용")
            @RequestParam(required = false) List<Long> partnerIds,
            @Parameter(description = "출고 창고 id (단건. 다중은 warehouseIds)")
            @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "출고 창고 id **다중선택**")
            @RequestParam(required = false) List<Long> warehouseIds,
            @Parameter(description = "키워드 — 거래처명·도서명·도서코드·매출번호·학교명을 한 번에 훑는다(부분일치)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "취소건 포함 여부(기본 false)") @RequestParam(defaultValue = "false") boolean includeCanceled,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(saleService.search(either(startDate, fromDate), either(endDate, toDate),
                MultiSelect.merge(salesCategory, salesCategories),
                MultiSelect.merge(tradeClass, tradeClasses),
                MultiSelect.merge(shipmentType, shipmentTypes),
                MultiSelect.merge(partnerId, partnerIds),
                MultiSelect.merge(warehouseId, warehouseIds),
                keyword,
                includeCanceled, pageReq.toPageable()));
    }

    @Operation(summary = "통합 매출 조회 엑셀 다운로드(12p)",
            description = """
                    조회(`GET /sales`)와 **같은 필터**를 받아 그 결과를 엑셀로 내린다.

                    ★필터를 똑같이 받는 이유: 다운로드만 조건이 빠지면 **화면에 없던 건이 파일에 실린다.**
                    담당자는 화면을 믿고 파일을 그대로 보고서에 붙인다.

                    ★페이징은 적용하지 않는다 — 파일은 **조건에 맞는 전량**이다.
                    화면 2페이지를 보다 내려받았는데 101~200행만 나오면 자료가 빠진 줄 모른다.

                    ‼️브라우저에서 `<a href>`로 이 주소를 열면 **인증 헤더가 실리지 않아 401**이다.
                    fetch로 토큰을 붙여 받은 뒤 파일로 저장해야 한다(개발팀 점검 2026-09-09 P0-5).""")
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportSales(
            @Parameter(description = "시작일(yyyy-MM-dd). `fromDate`로 보내도 된다")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @Parameter(description = "종료일(yyyy-MM-dd). `toDate`로 보내도 된다")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
            @Parameter(description = "시작일 별칭 — 다른 조회 API가 쓰는 이름")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일 별칭 — 다른 조회 API가 쓰는 이름")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) SalesCategory salesCategory,
            @RequestParam(required = false) List<SalesCategory> salesCategories,
            @RequestParam(required = false) TradeClass tradeClass,
            @RequestParam(required = false) List<TradeClass> tradeClasses,
            @RequestParam(required = false) ShipmentType shipmentType,
            @RequestParam(required = false) List<ShipmentType> shipmentTypes,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) List<Long> partnerIds,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "false") boolean includeCanceled) {
        List<Col> cols = List.of(
                new Col("매출번호", "salesNo"), new Col("매출일자", "salesDate"),
                new Col("거래처코드", "partnerCode"), new Col("도시명", "partnerCityName"),
                new Col("거래처명1", "partnerName1"), new Col("거래처명2", "partnerName"),
                new Col("거래처구분", "clientCategory"), new Col("지역", "region"),
                new Col("학교코드", "schoolCode"), new Col("학교/학원명", "schoolName"),
                new Col("대분류", "majorCategoryName"), new Col("세부구분", "salesDivisionName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "productCode"), new Col("도서명", "productName"),
                new Col("학년", "grade"), new Col("회차", "bookRound"),
                new Col("거래분류", "tradeClassName"), new Col("출고유형", "shipmentType"),
                new Col("창고", "warehouseName"),
                new Col("정가", "unitPrice"), new Col("공급률(%)", "supplyRate"),
                new Col("할인액", "discountAmount"), new Col("수량", "qty"),
                new Col("공급가액", "supplyAmount"), new Col("세액", "tax"),
                new Col("총금액", "totalAmount"),
                new Col("취소", "canceled"), new Col("메모", "memo"));

        // ★페이징 없이 전량. PageRequestDto 상한(200)에 걸리지 않도록 서비스에 직접 큰 페이지를 준다.
        var page = saleService.search(either(startDate, fromDate), either(endDate, toDate),
                MultiSelect.merge(salesCategory, salesCategories),
                MultiSelect.merge(tradeClass, tradeClasses),
                MultiSelect.merge(shipmentType, shipmentTypes),
                MultiSelect.merge(partnerId, partnerIds),
                MultiSelect.merge(warehouseId, warehouseIds),
                keyword,
                includeCanceled,
                org.springframework.data.domain.PageRequest.of(0, EXPORT_MAX));
        byte[] xlsx = excel.toXlsx("통합매출조회", cols, page.getContent(),
                Heading.period("통합 매출 조회", either(startDate, fromDate), either(endDate, toDate)));
        return excel.asDownload(xlsx, "통합매출조회.xlsx");
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

    @Operation(summary = "반품입고(29p·28p 물류 진입점, 교재식)",
            description = """
                    물류가 반품 물량을 입고하면 한 트랜잭션으로 매출 반품(RETURN) 라인 자동 생성
                    + 물류창고 재고 +복구. 반품 가능내역은 `GET /sales/returnable`로 먼저 조회한다.

                    ### ★초과해도 막지 않는다 — 경고로 알린다
                    발주처 화면검토(2026-08-31) 화면28 원문 —
                    "출고내역보다 반품 등록 내역이 더 많이 입력되는 경우 **경고 알림(alert)**을 넣어주시기 바랍니다."

                    경고를 요구했지 차단을 요구하지 않았고, 같은 회신에서 재고 음수·초과정산 차단도
                    함께 걷어냈다. 현장에서는 컷오버 전 출고분이나 다른 경로로 나간 물건이 반품으로 들어온다 —
                    막으면 **실제로 들어온 물건을 장부에 못 적는다**.

                    · 초과분은 응답 `warnings[]`에 담긴다(코드 `RETURN_EXCEEDS`,
                      반품가능수량·요청수량·초과수량·표시문구 포함). **`warnings`가 비어 있지 않으면 화면은 alert을 띄울 것.**
                    · 서버 로그에도 남는다. 조용히 통과시키면 담당자는 자기가 초과 입력한 줄 모른다.
                    · ⚠️종전에는 409 RETURN_EXCEEDS로 거부했다(2026-09-02 변경).

                    ### 공급률
                    원 출고건 값을 **표시**만 하고 **수정 가능**하다(발주처 확정 2026-08-04·08-05).
                    그래서 범위 판정은 **도서 단위 합계**로만 한다 — 공급률을 판정 키에 넣으면
                    담당자가 값을 바꾸는 순간 원 출고건을 못 찾아 거부되어 사실상 잠금이 된다.

                    재고관리 상품만 재고 복구(모의고사 등 인원기반은 이벤트 없음). 원본 출고번호(선택) 역추적 링크.""")
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
            @Parameter(description = "거래처 id 필터") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "구분 — 매출/반품/교사용/증정용. 미지정=전체. ‼️모르는 값은 400")
            @RequestParam(required = false) String summaryKind,
            @Parameter(description = "매출유형 — 일반매출/위탁매출. 미지정=전체. ‼️모르는 값은 400")
            @RequestParam(required = false) String salesType,
            @Parameter(description = "키워드 — 코드·명칭을 함께 훑는다(부분일치)")
            @RequestParam(required = false) String keyword) {
        SalesSummaryResponse r = saleReportService.summary(fromDate, toDate, partnerId,
                summaryKind, salesType);
        return ApiResponse.success(new SalesSummaryResponse(r.fromDate(), r.toDate(),
                Keywords.filter(r.rows(), keyword,
                        x -> new Object[]{x.productCode(), x.productName()}),
                r.total()));
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
            @Parameter(description = "콘텐츠구분(SELF/EXTERNAL)") @RequestParam(required = false) String contentType,
            @Parameter(description = "키워드 — 코드·명칭을 함께 훑는다(부분일치)")
            @RequestParam(required = false) String keyword) {
        NetSalesResponse r = saleReportService.netSales(fromDate, toDate, contentType);
        return ApiResponse.success(new NetSalesResponse(r.fromDate(), r.toDate(), r.contentType(),
                Keywords.filter(r.rows(), keyword,
                        x -> new Object[]{x.productCode(), x.productName(), x.catCode(), x.catName()}),
                r.total()));
    }

    @Operation(summary = "콘텐츠구분 순매출 엑셀 다운로드",
            description = "드라이브 순매출조회 형식(상품별 매출·반품·순매출 + 외부콘텐츠 매입·이익·이익률).")
    @GetMapping("/net-summary/export")
    public ResponseEntity<byte[]> netSummaryExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String contentType) {
        List<Col> cols = List.of(
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("상품코드", "productCode"), new Col("상품명", "productName"), new Col("콘텐츠구분", "contentType"),
                new Col("매출수량", "saleQty"), new Col("매출 공급가액", "saleAmount"),
                new Col("교사용수량", "freeQty"), new Col("교사용 공급가액", "freeAmount"),
                new Col("반품수량", "returnQty"), new Col("반품률(%)", "returnRate"), new Col("반품액", "returnAmount"),
                new Col("순매출수량", "netQty"), new Col("순매출 공급가액", "netAmount"),
                new Col("순매출 세액", "netTax"), new Col("순매출 총금액", "netTotal"),
                new Col("매입 입고수량", "inboundQty"),
                new Col("매입단가", "purchaseUnitCost"), new Col("매입액", "purchaseAmount"),
                new Col("이익", "profit"), new Col("이익률(%)", "marginPct"));
        byte[] xlsx = excel.toXlsx("순매출조회", cols, saleReportService.netSales(fromDate, toDate, contentType).rows(),
                Heading.period("순매출조회", fromDate, toDate));
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
            @Parameter(description = """
                    구분 SALE(매출)/RETURN(반품)/TEACHER_USE(교사용)/GIFT(증정용). 미지정=전체.
                    ‼️표준 '구분(상세)'(3종)와 값이 다르다 — 무상을 교사용·증정용으로 쪼갠 축이다(정본 15p).""")
            @RequestParam(required = false) StatementKind kind,
            @Parameter(description = "매출유형 NORMAL_SALES(일반매출)/CONSIGN_SALES(위탁매출). 미지정=전체")
            @RequestParam(required = false) SalesType salesType) {
        return ApiResponse.success(saleReportService.statement(from, to, kind, salesType));
    }

    @Operation(summary = "매출액명세서 엑셀 다운로드", description = "드라이브 '매출액정리' 형식(분류/도서별 수량·금액·세액·합계).")
    @GetMapping("/statement/export")
    public ResponseEntity<byte[]> statementExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) StatementKind kind,
            @RequestParam(required = false) SalesType salesType) {
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("대분류", "majorName"),
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "bookCode"), new Col("도서명", "bookName"), new Col("수량", "qty"),
                new Col("금액", "amount"), new Col("세액", "tax"), new Col("합계", "total"));
        byte[] xlsx = excel.toXlsx("매출액명세서", cols,
                saleReportService.statement(from, to, kind, salesType).rows(),
                Heading.period("매출액명세서", from, to));
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
        byte[] xlsx = excel.toXlsx("월별매출액명세서", cols, saleReportService.monthlyStatement(y, m).rows(),
                new Heading("월별매출액명세서", "조회기준 : " + y + "년 " + m + "월"));
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
            @Parameter(description = "분류코드 필터(미지정=전체)") @RequestParam(required = false) String catCode,
            @Parameter(description = "키워드 — 코드·명칭을 함께 훑는다(부분일치)")
            @RequestParam(required = false) String keyword) {
        YoyComparisonResponse r = saleReportService.yoyComparison(from, to, groupBy, partnerId, catCode);
        return ApiResponse.success(new YoyComparisonResponse(r.from(), r.to(), r.prevFrom(), r.prevTo(),
                r.groupBy(),
                Keywords.filter(r.rows(), keyword,
                        x -> new Object[]{x.partnerCode(), x.partnerName(), x.catCode(), x.catName(),
                                x.bookCode(), x.bookName()})));
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

    @Operation(summary = "회차별 포장유형 물량(구 IC회차별작업현황) — ⚠️화면30이 아니다",
            description = """
                    분류×도서×**회차**를 행으로, 포장구분(개별1/개별2/반별) 수량을 열로 펼쳐 보여준다.
                    레거시 IC회차별작업현황 화면 재현 — 회차가 없는 건(0)은 제외되고, 취소 건도 빠진다.
                    catCode를 주면 해당 분류만 조회한다.

                    ### ⚠️화면30(회차별작업현황)은 이제 이 API가 아니다
                    발주처가 화면검토(2026-08-31)에서 "이 화면이 세트 조립·해체를 추적하는 화면인지,
                    아니면 회차별 포장유형별 물량을 집계하는 화면인지" 확인을 요청했고,
                    **실측 결과 후자(이 API)였다.** 그래서 요청대로 화면30은
                    **`GET /stock/bom-work-status`(세트 조립·해체 현황)** 으로 재구성했다.

                    이 API는 **레거시 재현으로서 그대로 남겨 둔다** — 포장유형별 물량은 물류에서
                    여전히 쓰는 숫자이고, 지운다고 조립·해체 현황이 더 정확해지지 않는다.""")
    @GetMapping("/round-work-status")
    public ApiResponse<List<RoundWorkStatusRow>> roundWorkStatus(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "분류코드(선택)") @RequestParam(required = false) String catCode,
            @Parameter(description = "키워드 — 코드·명칭을 함께 훑는다(부분일치)")
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(Keywords.filter(
                saleReportService.roundWorkStatus(fromDate, toDate, catCode), keyword,
                r -> new Object[]{r.catCode(), r.catName(), r.productCode(), r.productName()}));
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
            @RequestParam(required = false) Long partnerId,
            @Parameter(description = "키워드 — 코드·명칭을 함께 훑는다(부분일치)")
            @RequestParam(required = false) String keyword) {
        AttendancePeriodResponse r = attendanceService.period(fromDate, toDate, grade, partnerId);
        return ApiResponse.success(new AttendancePeriodResponse(r.fromDate(), r.toDate(), r.months(),
                Keywords.filter(r.rows(), keyword,
                        x -> new Object[]{x.region(), x.partnerCode(), x.partnerName(),
                                x.schoolCode(), x.schoolName()})));
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

    /**
     * 날짜 파라미터 별칭 합치기.
     *
     * <p>★{@code /sales}는 {@code startDate/endDate}, 나머지 조회 API는 {@code fromDate/toDate}를
     * 쓰고 있었다. 이름을 한쪽으로 바꾸면 이미 붙어 있는 화면이 조용히 전체기간 조회가 된다
     * (파라미터가 안 맞으면 400이 아니라 <b>null이 되어 필터가 사라진다</b>).
     * 그래서 바꾸지 않고 <b>둘 다 받는다.</b> 둘 다 오면 원래 이름을 쓴다.
     */
    private static LocalDate either(LocalDate primary, LocalDate alias) {
        return (primary != null) ? primary : alias;
    }
}
