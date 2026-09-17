package com.daesung.sales.inventory.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.excel.ExcelExportUtil.Heading;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.BomWorkRequest;
import com.daesung.sales.inventory.dto.BomWorkResponse;
import com.daesung.sales.inventory.dto.InboundRequest;
import com.daesung.sales.inventory.dto.InboundResponse;
import com.daesung.sales.inventory.dto.StockLedgerRow;
import com.daesung.sales.inventory.dto.StockRecordRow;
import com.daesung.sales.inventory.dto.StockSettlementRow;
import com.daesung.sales.inventory.dto.TransferRequest;
import com.daesung.sales.inventory.dto.TransferResponse;
import com.daesung.sales.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import com.daesung.sales.common.query.MultiSelect;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주문/출고관리 - 재고(입고/이고/BOM). 실제 경로: /api/v1/stock. */
@Tag(name = "주문/출고관리 · 재고", description = "재고 엔진(로직 A): 입고/이고/BOM")
@RestController
@RequiredArgsConstructor
@RequestMapping("/stock")
public class InventoryController {

    private final InventoryService inventoryService;
    private final com.daesung.sales.inventory.service.MaterialLedgerService materialLedgerService;
    private final ExcelExportUtil excel;

    @Operation(summary = "일반 입고 등록",
            description = "인쇄소 등 → 물류창고 입고. 재고이벤트(INBOUND) 기록 + 재고 잔량 가산을 한 트랜잭션으로 처리")
    @PostMapping("/inbound")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InboundResponse> inbound(@Valid @RequestBody InboundRequest req) {
        return ApiResponse.success(inventoryService.inbound(req));
    }

    @Operation(summary = "단순 이고(창고 이동)",
            description = "출발창고 −qty(음수재고 방지) / 도착창고 +qty. 매출 미발생. 재고이벤트 2다리를 한 트랜잭션으로.")
    @PostMapping("/transfer")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<TransferResponse> transfer(@Valid @RequestBody TransferRequest req) {
        return ApiResponse.success(inventoryService.transfer(req));
    }

    @Operation(summary = "세트 조립/해체(BOM)",
            description = "조립=완제품+/구성품−, 해체=반대. 구성품·비율은 상품 BOM 마스터에서 읽음. 음수재고 방지, 한 트랜잭션.")
    @PostMapping("/bom")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BomWorkResponse> bom(@Valid @RequestBody BomWorkRequest req) {
        return ApiResponse.success(inventoryService.bom(req));
    }

    @Operation(summary = "입고/대체 내역 조회(8p·9p)",
            description = """
                    등록된 **입고·단순이고·세트조립/해체**를 최근순으로. 기간·도서·창고·구분으로 좁힌다.

                    ★**등록만 되고 조회가 없었다.** 전부 재고를 움직이는 작업인데 되짚어 볼
                    방법이 없으면, 재고가 안 맞을 때 언제 무엇이 들어오고 옮겨졌는지 알 수 없다.

                    수불부(`/stock/ledger`)는 **합계**를 주고 이건 **낱건**을 준다 —
                    합계가 이상할 때 그 안을 들여다보는 용도라 둘 다 필요하다.

                    ★수량 부호는 **그대로** 준다. 이고는 출발(−)·도착(+) 두 줄이고
                    세트작업도 완제품(+)·구성품(−)으로 갈린다 — 방향이 곧 정보다.

                    출고·폐기·반품·실사는 제외한다(각자 화면이 따로 있다).

                    도서·창고·작업구분 모두 **다중선택**이다(좌측 트리뷰 체크박스, 2026-08-31 공통 요구).
                    단수 파라미터도 그대로 살아 있고, 복수와 같이 오면 합집합이다.""")
    @GetMapping("/records")
    public ApiResponse<List<StockRecordRow>> records(
            @Parameter(description = "처리일 시작(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "처리일 종료(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "도서(상품) id 필터(단건)") @RequestParam(required = false) Long productId,
            @Parameter(description = "도서(상품) id **다중선택**") @RequestParam(required = false)
            List<Long> productIds,
            @Parameter(description = "창고 id 필터(단건)") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "창고 id **다중선택**") @RequestParam(required = false)
            List<Long> warehouseIds,
            @Parameter(description = "작업구분 INBOUND(입고)/TRANSFER(단순이고)/"
                    + "BOM_ASSEMBLE(세트조립)/BOM_DISASSEMBLE(세트해체). 미지정=전체 (단건)")
            @RequestParam(required = false) com.daesung.sales.inventory.entity.TxnType kind,
            @Parameter(description = "작업구분 **다중선택** — 예: INBOUND,TRANSFER")
            @RequestParam(required = false) List<com.daesung.sales.inventory.entity.TxnType> kinds) {
        return ApiResponse.success(inventoryService.stockRecords(fromDate, toDate,
                MultiSelect.merge(productId, productIds),
                MultiSelect.merge(warehouseId, warehouseIds),
                MultiSelect.merge(kind, kinds)));
    }

    @Operation(summary = "제품수불부 조회",
            description = "이월+입고+이고+BOM+폐기(+출고) = 현재재고 단일 공식 집계. "
                    + "이벤트합계(closing)와 캐시(inventory.qty) 대사(reconciled) 포함. "
                    + "기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/ledger")
    public ApiResponse<com.daesung.sales.common.response.PageResponse<StockLedgerRow>> ledger(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "상품 id 필터") @RequestParam(required = false) Long productId,
            @Parameter(description = "창고 id 필터") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = """
                    창고구분 MAIN(물류창고)/CONSIGN(위탁창고). 미지정=전체.
                    발주처 요청(2026-08-21): 전체 합산만 보면 **위탁 미결잔여가 실제로 어느 창고에
                    남아 있는지** 알 수 없다.""")
            @RequestParam(required = false) com.daesung.sales.warehouse.entity.WarehouseType warehouseType,
            @Parameter(description = "키워드 — 도서코드·도서명·창고명을 한 번에 훑는다(부분일치)")
            @RequestParam(required = false) String keyword,
            @Parameter(description = """
                    정렬 — `필드,방향` (예: `closing,desc`). 방향 생략 시 오름차순.
                    필드: productCode · productName · warehouseName · opening · inbound ·
                    sale · netSaleQty · salesReturn · dispose · closing.
                    ‼️모르는 필드는 400 — 조용히 기본 순서로 주면 정렬이 먹은 줄 알고 위에서부터 판단한다.""")
            @RequestParam(required = false) String sort,
            @Parameter(description = "페이지(0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "100") int size) {
        // ★집계 후 자른다. SQL에 LIMIT을 걸면 페이지마다 이월·마감이 달라진다
        //   (수불부는 상품×창고로 묶은 뒤라야 이월이 정확하다). 총건수는 전체를 준다.
        return ApiResponse.success(com.daesung.sales.common.response.PageResponse.ofList(
                inventoryService.stockLedger(fromDate, toDate, productId, warehouseId,
                        warehouseType, keyword, sort),
                page, size));
    }

    @Operation(summary = "제품수불부 엑셀 다운로드", description = "이월/입고/이고/조립해체/폐기/매출/무상/교사용/반품/조정/현재재고.")
    @GetMapping("/ledger/export")
    public ResponseEntity<byte[]> ledgerExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long productId, @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) com.daesung.sales.warehouse.entity.WarehouseType warehouseType) {
        List<Col> cols = List.of(
                new Col("도서코드", "productCode"), new Col("도서명", "productName"), new Col("창고", "warehouseName"),
                new Col("이월", "opening"), new Col("입고", "inbound"), new Col("이고", "transfer"),
                new Col("조립해체", "bom"), new Col("폐기", "dispose"), new Col("매출", "sale"),
                new Col("무상", "free"), new Col("교사용", "teacher"), new Col("반품", "salesReturn"),
                new Col("조정", "adjust"), new Col("순매출수량", "netSaleQty"),
                new Col("현재재고", "closing"));
        byte[] xlsx = excel.toXlsx("제품수불부", cols,
                inventoryService.stockLedger(fromDate, toDate, productId, warehouseId, warehouseType),
                Heading.period("제품수불부현황", fromDate, toDate));
        return excel.asDownload(xlsx, "제품수불부.xlsx");
    }

    @Operation(summary = "제품수불부 자재 상세(11p 2단계)",
            description = """
                    요약(세트·회차)에서 한 행을 골랐을 때 **구성 자재가 몇 장 들어갔는지**.

                    ★**자재 재고가 아니다.** 발주처 원문 — "자재 자체의 입고·이월을 반영한
                    재고 잔량이 아니라 해당 세트 내 **소요량** 기준".
                    자재를 얼마나 들여왔는지가 아니라, 세트가 팔린 만큼 얼마나 쓰였는지를 낸다.

                    합계 = **세트 출고분 + 회차 단독 출고분**
                    (각각 그 상품의 출고수량 × 세트당 소요수량)

                    출고수량 = 매출 + 무상 + 교사용 + 폐기 − 반품 ± 세트조립/해체.
                    ‼️**창고이동은 빠진다** — 창고만 옮긴 것은 소비가 아니다(발주처 확정).

                    회차를 지정하면 그 회차 전용 자재 + 공통 자재만 나온다.

                    ### 공통 자재의 '회차 단독 출고분'
                    구조보완요청안 각주가 공통 자재를 **두 종류**로 나눠 놓았다 —
                    "회차마다 반복 사용되는 자재(OMR, **4회차 기준 4**)는 회차 수만큼 반영한 값을,
                    세트 전체에 한 번만 필요한 자재(쿠폰)는 **1**로 고정한 값을 입력".
                    숫자만으로는 `4`가 "4회차×1"인지 "세트당 4개 고정"인지 구분되지 않아,
                    자재 매칭에 **회차반복(`perRound`)** 플래그를 둔다(V61).

                    · **반복형** — 회차당 소요 = 세트당수량 ÷ 회차수(OMR 4 ÷ 4회차 = 1).
                      회차만 단독으로 팔려도 그만큼 나간다.
                    · **1회형**(기본) — 0. 세트를 사야 붙는 자재라 회차만 사면 나가지 않는다.

                    ‼️회차 수는 **필터 전 세트 전체 기준**으로 센다. 1회만 골랐다고 회차수가 1이 되면
                    OMR이 4장으로 뻥튀기된다.""")
    @GetMapping("/ledger/materials")
    public ApiResponse<com.daesung.sales.inventory.dto.MaterialLedgerResponse> materials(
            @Parameter(description = "세트 상품 id", required = true) @RequestParam Long setProductId,
            @Parameter(description = "회차 상품 id(미지정 시 세트 전체 자재)")
            @RequestParam(required = false) Long roundProductId,
            @Parameter(description = "시작일(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(
                materialLedgerService.materials(setProductId, roundProductId, fromDate, toDate));
    }

    @Operation(summary = "제품수불부 결산내역(연초~기준일 누적)",
            description = """
                    기준일자 연도 1월 1일부터 기준일까지의 수불 전체내역. 분류 소계·총계 포함.
                    레거시 제품수불부 「결산내역」 체크박스와 같은 뷰다 —
                    화면 안내문 원문 "&lt;결산내역&gt; 체크시 기준일자 연도 1월1일부터 기준일자 까지의
                    제품수불 전체내역을 보여줍니다".
                    ★시작일은 받지 않는다(연초 고정). 분류 필터도 없다(레거시는 결산 시 분류 콤보를 잠근다).
                    창고는 합산하되 창고구분 필터는 남긴다.""")
    @GetMapping("/ledger/settlement")
    public ApiResponse<List<StockSettlementRow>> settlement(
            @Parameter(description = "기준일(yyyy-MM-dd, 미지정 시 오늘). 시작일은 이 날짜의 연도 1/1로 고정")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @Parameter(description = "상품 id 필터") @RequestParam(required = false) Long productId,
            @Parameter(description = "창고구분 MAIN/CONSIGN. 미지정=전체")
            @RequestParam(required = false) com.daesung.sales.warehouse.entity.WarehouseType warehouseType) {
        return ApiResponse.success(inventoryService.stockSettlement(baseDate, productId, warehouseType));
    }

    @Operation(summary = "제품수불부 결산내역 엑셀 다운로드",
            description = "연초~기준일 누적 + 분류 소계·총계.")
    @GetMapping("/ledger/settlement/export")
    public ResponseEntity<byte[]> settlementExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate baseDate,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) com.daesung.sales.warehouse.entity.WarehouseType warehouseType) {
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "productCode"), new Col("도서명", "productName"),
                new Col("이월", "opening"), new Col("입고", "inbound"), new Col("이고", "transfer"),
                new Col("조립해체", "bom"), new Col("폐기", "dispose"), new Col("매출", "sale"),
                new Col("무상", "free"), new Col("교사용", "teacher"), new Col("반품", "salesReturn"),
                new Col("조정", "adjust"), new Col("재고", "closing"));
        byte[] xlsx = excel.toXlsx("제품수불부결산", cols,
                inventoryService.stockSettlement(baseDate, productId, warehouseType),
                Heading.asOf("제품수불부 결산내역", baseDate));
        return excel.asDownload(xlsx, "제품수불부_결산내역.xlsx");
    }

    private static final String BOM_WORK_DESC = """
            세트를 **조립·해체한 결과**를 세트별로 집계한다.
            근거: 발주처 화면검토(2026-08-31) 화면30 — "화면 27(물류 작업비 계산)의 비용 산출과는
            **연동되지 않도록 분리**해, 세트 조립·해체 작업을 진행한 **현황(결과)만** 보여주는 조회 화면".

            ### ★비용 칸이 없다
            발주처가 요구한 '분리'는 화면을 나누는 것이 아니라 **이 화면이 비용을 말하지 않는 것**이다.
            금액 칸이 하나라도 있으면 화면27과 값이 갈리는 순간 어느 쪽이 맞는지 다투게 된다.
            작업비는 화면27(물류작업비) 소관이다.

            ### 무엇을 세나
            · 작업 건수 = **완제품 이벤트 수**(작업 1회에 정확히 하나 생긴다)
            · 조립 수량 / 해체 수량 / **순증**(조립 − 해체). 순증이 음수면 그 기간에 푼 쪽이 많다.
            · `components` = 그 기간 조립·해체로 움직인 **구성품 합계**
              ⚠️**어느 세트 작업에 쓰였는지는 나누지 않는다.** 원장에 완제품↔구성품 링크가 없다.
              지금 BOM 비율로 역산하면 그럴듯한 숫자가 나오지만, 작업한 뒤 BOM이 바뀌었으면
              실제와 다른 값을 사실처럼 보여주게 된다. 모르는 것은 모른다고 둔다.

            ### ⚠️이전 구현을 대체한다
            종전 화면30은 **회차별 포장유형(개별1/개별2/반별) 물량 집계**였다. 레거시
            `IC회차별작업현황.vb`를 그대로 옮긴 것이라 레거시로서는 맞았지만, 발주처가
            "후자라면 조립·해체 현황으로 구성해 달라"고 확인 요청했고 실측 결과 후자였다.
            포장유형 집계가 필요하면 `GET /sales/round-work-status`가 그대로 남아 있다.""";

    @Operation(summary = "세트 조립·해체 현황(30p)", description = BOM_WORK_DESC)
    @GetMapping("/bom-work-status")
    public ApiResponse<com.daesung.sales.inventory.dto.BomWorkStatusResponse> bomWorkStatus(
            @Parameter(description = "작업일 시작(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "작업일 종료(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "창고 id 필터(단건)") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "창고 id **다중선택**") @RequestParam(required = false)
            List<Long> warehouseIds,
            @Parameter(description = "분류코드 필터") @RequestParam(required = false) String catCode) {
        return ApiResponse.success(inventoryService.bomWorkStatus(fromDate, toDate,
                MultiSelect.merge(warehouseId, warehouseIds), catCode));
    }

    @Operation(summary = "세트 조립·해체 현황 엑셀 다운로드(30p)",
            description = "세트별 조립·해체 수량. 작업비는 담지 않는다(화면27 소관).")
    @GetMapping("/bom-work-status/export")
    public ResponseEntity<byte[]> bomWorkStatusExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) List<Long> warehouseIds,
            @RequestParam(required = false) String catCode) {
        List<Col> cols = List.of(
                new Col("분류코드", "catCode"), new Col("분류명", "catName"),
                new Col("도서코드", "productCode"), new Col("도서명", "productName"),
                new Col("조립건수", "assembleCount"), new Col("조립수량", "assembleQty"),
                new Col("해체건수", "disassembleCount"), new Col("해체수량", "disassembleQty"),
                new Col("순증", "netQty"), new Col("최근작업일", "lastWorkedAt"));
        byte[] xlsx = excel.toXlsx("세트조립해체현황", cols,
                inventoryService.bomWorkStatus(fromDate, toDate,
                        MultiSelect.merge(warehouseId, warehouseIds), catCode).rows(),
                Heading.period("세트 조립·해체 현황", fromDate, toDate));
        return excel.asDownload(xlsx, "세트_조립해체현황.xlsx");
    }
}
