package com.daesung.sales.consignment.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.consignment.dto.ConsignPendingResponse;
import com.daesung.sales.consignment.dto.ConsignReturnRequest;
import com.daesung.sales.consignment.dto.ConsignReturnResponse;
import com.daesung.sales.consignment.dto.ConsignSettleRequest;
import com.daesung.sales.consignment.dto.ConsignSettleResponse;
import com.daesung.sales.consignment.dto.ConsignmentOutRequest;
import com.daesung.sales.consignment.dto.ConsignmentOutResponse;
import com.daesung.sales.consignment.dto.SettlementStatementResponse;
import com.daesung.sales.consignment.service.ConsignmentService;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 위탁관리(로직 B). 실제 경로: /api/v1/consignment. 스펙 §3-2(consign-pending/from-consign) 대응. */
@Tag(name = "위탁관리 · 미결정산", description = "로직 B: 위탁출고(자동이고+미결생성) / 미결조회 / 부분정산(매출확정)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/consignment")
public class ConsignmentController {

    private final ConsignmentService consignmentService;
    private final ExcelExportUtil excel;

    @Operation(summary = "위탁출고 등록",
            description = "물류창고 → 위탁창고 이고(재고 이동) + 미결원장(consignment_out, OPEN) 생성. "
                    + "매출은 발생하지 않음 — 정산 시점에 확정.")
    @PostMapping("/out")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConsignmentOutResponse> out(@Valid @RequestBody ConsignmentOutRequest req) {
        return ApiResponse.success(consignmentService.consignmentOut(req));
    }

    @Operation(summary = "위탁 미결 조회",
            description = "거래처의 미결(잔여>0) 위탁출고 목록. 정산 화면에서 불러온다.")
    @GetMapping("/pending")
    public ApiResponse<ConsignPendingResponse> pending(
            @Parameter(description = "위탁 거래처 id", example = "1") @RequestParam Long partnerId) {
        return ApiResponse.success(consignmentService.findPending(partnerId));
    }

    @Operation(summary = "위탁 미결정산",
            description = """
                    위탁 미결을 정산(매출 확정)하거나 반품(재고 복귀)한다. **한 줄에서 둘 다 입력 가능**하다.

                    · **정산수량** → 매출이 확정된다(거래처가 실제 판매분 정산 데이터를 보내온 경우).
                    · **반품수량** → **매출에 영향 없이** 미결 잔여만 줄고 실물재고가 위탁창고에서 물류창고로 돌아온다.
                    · 정산수량 + 반품수량이 미결 잔여를 넘으면 409. **합쳐서** 검사하므로
                      각각은 잔여 이내여도 합이 넘으면 거부된다.
                    · 이미 매출로 확정된 분의 반품(Case 1)은 여기가 아니라 반품입고로 처리한다.

                    근거: 발주처 확정(확인요청서 v1 2번 No.4 · 이슈#43, 2026-07-29)"""
                    + "정산수량이 미결 잔여를 초과하면 409(OVER_SETTLEMENT).")
    @PostMapping("/settle")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConsignSettleResponse> settle(@Valid @RequestBody ConsignSettleRequest req) {
        return ApiResponse.success(consignmentService.settle(req));
    }

    @Operation(summary = "위탁 반품(미정산분)",
            description = "미결(미판매) 잔여를 위탁창고→물류창고로 역-자동이고(재고 복귀) + 미결원장 축소. "
                    + "매출 무관. 반품수량이 미결 잔여 초과 시 409. 판매완료분 반품은 반품입고(/sales/return-inbound).")
    @PostMapping("/return")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConsignReturnResponse> returnConsignment(@Valid @RequestBody ConsignReturnRequest req) {
        return ApiResponse.success(consignmentService.returnConsignment(req));
    }

    @Operation(summary = "정산내역서 조회",
            description = "기간 내 위탁 부분정산 이력 + 연결 매출금액(공급가·세액·총금액) + 미결원장 현황(총출고/기정산/미결잔여) "
                    + "+ 합계. 위탁 회계기준 '정산 시점 매출'. 기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/settlement-statement")
    public ApiResponse<SettlementStatementResponse> settlementStatement(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(consignmentService.settlementStatement(fromDate, toDate));
    }

    @Operation(summary = "정산내역서 엑셀 다운로드", description = "위탁정산 이력 + 매출금액 + 미결현황 xlsx.")
    @GetMapping("/settlement-statement/export")
    public ResponseEntity<byte[]> settlementStatementExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<Col> cols = List.of(
                new Col("정산일", "settledDate"), new Col("거래처명", "partnerName"),
                new Col("도서코드", "productCode"), new Col("도서명", "productName"),
                new Col("원본출고번호", "sourceOutNo"), new Col("정산수량", "settleQty"), new Col("매출번호", "salesRefNo"),
                new Col("공급가액", "supplyAmount"), new Col("세액", "tax"), new Col("총금액", "totalAmount"),
                new Col("총출고", "totalQty"), new Col("기정산", "settledQtyCum"),
                new Col("미결잔여", "remainingQty"), new Col("상태", "status"));
        byte[] xlsx = excel.toXlsx("정산내역서", cols,
                consignmentService.settlementStatement(fromDate, toDate).rows());
        return excel.asDownload(xlsx, "정산내역서.xlsx");
    }
}
