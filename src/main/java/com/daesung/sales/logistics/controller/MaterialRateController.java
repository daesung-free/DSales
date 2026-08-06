package com.daesung.sales.logistics.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.logistics.dto.AssemblyCostRow;
import com.daesung.sales.logistics.dto.MaterialRateRequest;
import com.daesung.sales.logistics.dto.MaterialRateResponse;
import com.daesung.sales.logistics.service.MaterialRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 물류 · 자재단가 및 세트 조립 작업비. 실제 경로: /api/v1/logistics-costs.
 * 근거: 발주처 확정 2026-08-05 — 조립 작업비는 등록 단가 기준 자동계산, 물류팀은 내역을 다운로드해 정산 요청.
 */
@Tag(name = "물류 · 자재단가/조립작업비", description = "세트 조립 작업비 자동계산의 단가 관리와 계산 내역 조회")
@RestController
@RequiredArgsConstructor
@RequestMapping("/logistics-costs")
public class MaterialRateController {

    private final MaterialRateService service;
    private final ExcelExportUtil excel;

    @Operation(summary = "자재 단가 목록",
            description = "자재구분(시험지·해설지·OMR·라벨)×작업구분별 단가. 작업구분 0 = 공통 단가.")
    @GetMapping("/material-rates")
    public ApiResponse<List<MaterialRateResponse>> rates() {
        return ApiResponse.success(service.list());
    }

    @Operation(summary = "자재 단가 등록·수정",
            description = """
                    자재구분×작업구분 단위로 upsert 한다(같은 조합이 두 벌 생기지 않는다).
                    작업구분을 비우면 **공통 단가**로 저장되고, 특정 작업구분 단가가 없을 때 이 값이 쓰인다.""")
    @PutMapping("/material-rates")
    public ApiResponse<MaterialRateResponse> upsertRate(@Valid @RequestBody MaterialRateRequest req) {
        return ApiResponse.success(service.upsert(req));
    }

    @Operation(summary = "자재 단가 삭제")
    @DeleteMapping("/material-rates/{id}")
    public ApiResponse<Void> deleteRate(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }

    @Operation(summary = "세트 조립 작업비 내역",
            description = """
                    기간 내 세트 조립 건의 자동계산된 작업비.
                    작업비는 **조립 시점에 확정되어 저장**된다 — 단가표나 BOM이 나중에 바뀌어도
                    이미 끝난 작업의 금액은 변하지 않는다(물류팀이 그 금액으로 정산을 요청했을 수 있다).""")
    @GetMapping("/assembly")
    public ApiResponse<List<AssemblyCostRow>> assembly(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "창고 id(선택)") @RequestParam(required = false) Long warehouseId) {
        return ApiResponse.success(service.assemblyCosts(fromDate, toDate, warehouseId));
    }

    @Operation(summary = "세트 조립 작업비 엑셀 다운로드",
            description = "물류팀이 받아 가공·정산 요청하는 내역.")
    @GetMapping("/assembly/export")
    public ResponseEntity<byte[]> assemblyExport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long warehouseId) {
        List<Col> cols = List.of(
                new Col("작업일자", "workDate"), new Col("창고", "warehouseName"),
                new Col("세트코드", "productCode"), new Col("세트명", "productName"),
                new Col("조립수량", "workQty"), new Col("작업비", "workCost"), new Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("조립작업비", cols, service.assemblyCosts(fromDate, toDate, warehouseId));
        return excel.asDownload(xlsx, "세트조립작업비.xlsx");
    }
}
