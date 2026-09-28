package com.daesung.sales.inventory.controller;

import org.springframework.web.bind.annotation.PathVariable;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalResponse;
import com.daesung.sales.inventory.service.InventoryService;
import io.swagger.v3.oas.annotations.Operation;
import com.daesung.sales.common.query.MultiSelect;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 주문/출고관리 - 폐기. 실제 경로: /api/v1/disposals. */
@Tag(name = "주문/출고관리 · 폐기", description = "연마감 폐기 등록(재고 즉시 차감)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/disposals")
public class DisposalController {

    private final InventoryService inventoryService;
    private final com.daesung.sales.common.audit.CurrentAuditor currentAuditor;
    private final com.daesung.sales.common.excel.ExcelExportUtil excel;
    private final com.daesung.sales.inventory.service.DisposalUploadService disposalUploadService;

    @Operation(summary = "전표 삭제(마감 前)",
            description = """
                    **잘못 입력한 전표를 없던 것으로** 만든다(발주처 2026-08-14 [4] "마감 확정 前 삭제 가능").

                    ★취소와 다른 축이다 — 취소는 "되돌렸다"를 반대 이벤트로 장부에 남기고,
                    삭제는 애초에 없던 일로 만든다(원 이벤트를 무효화하고 잔량만 되돌린다).

                    · **사유는 선택**(9/27 A-3, 항목 9). 지운 품목·수량은 상태변경 이력에 남는다.
                    · **이미 취소된 전표는 400** — 되돌린 기록이 장부에 선 뒤라 오입력이 아니다.
                    · **마감된 달은 400**(PERIOD_LOCKED).

                    ⚠️{@code inventory_txn} 행은 남는다(논리삭제). 재고의 유일 진실이라
                    물리삭제하면 수불부·채권이 파생되는 원장을 찢는 것과 같다.""")
    @DeleteMapping("/{refNo}")
    public ApiResponse<Void> deleteVoucher(
            @Parameter(description = "전표번호", required = true) @PathVariable String refNo,
            @Valid @RequestBody(required = false) com.daesung.sales.logistics.dto.ReasonRequest req) {
        inventoryService.deleteVoucher(refNo, com.daesung.sales.logistics.dto.ReasonRequest.reasonOf(req), currentAuditor.username());
        return ApiResponse.success(null);
    }

    @Operation(summary = "폐기 등록",
            description = "등록 수량만큼 재고 즉시 차감(음수재고 방지) + 폐기번호(P) 채번. 한 트랜잭션.")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<DisposalResponse> dispose(@Valid @RequestBody DisposalRequest req) {
        return ApiResponse.success(inventoryService.dispose(req));
    }

    @Operation(summary = "폐기 내역 조회(10p)",
            description = """
                    등록된 폐기 전표를 최근순으로. 기간·도서·창고로 좁힐 수 있다(전부 선택).

                    ★**등록만 되고 조회가 없었다.** 폐기는 재고를 깎는 전표라
                    "무엇을 언제 왜 버렸는지"를 되짚을 수 없으면 재고가 안 맞을 때
                    원인을 찾을 방법이 없다.

                    수량은 **양수**로 준다(원장에는 음수로 기록된다 — 재고를 깎으므로).

                    도서·창고는 **다중선택**이다(좌측 트리뷰 체크박스, 2026-08-31 공통 요구).
                    단수 `productId`·`warehouseId`도 그대로 살아 있고, 복수와 같이 오면 합집합이다.""")
    @GetMapping
    public ApiResponse<com.daesung.sales.common.response.PageResponse<com.daesung.sales.inventory.dto.DisposalRecordRow>> list(
            @Parameter(description = "폐기일 시작(yyyy-MM-dd). 미지정=전체")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "폐기일 종료(yyyy-MM-dd). 미지정=전체")
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "도서(상품) id 필터(단건)") @RequestParam(required = false) Long productId,
            @Parameter(description = "도서(상품) id **다중선택**") @RequestParam(required = false)
            java.util.List<Long> productIds,
            @Parameter(description = "창고 id 필터(단건)") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "창고 id **다중선택**") @RequestParam(required = false)
            java.util.List<Long> warehouseIds,
            @org.springdoc.core.annotations.ParameterObject
            com.daesung.sales.common.dto.PageRequestDto page) {
        return ApiResponse.success(com.daesung.sales.common.response.PageResponse.of(
                inventoryService.disposals(fromDate, toDate,
                        MultiSelect.merge(productId, productIds),
                        MultiSelect.merge(warehouseId, warehouseIds),
                        page.toPageable(DISPOSAL_SORTS))));
    }

    /** 정렬 별칭. 화면이 보내는 컬럼명을 엔티티 경로로 바꾼다(모르는 키는 400 + 가능한 목록). */
    private static final java.util.Map<String, String> DISPOSAL_SORTS = java.util.Map.of(
            "date", "tradeDate", "tradeDate", "tradeDate",
            "refNo", "refNo", "qty", "qty",
            "bookCode", "product.code", "bookName", "product.name",
            "warehouse", "warehouse.name", "id", "id");

    @Operation(summary = "폐기 내역 엑셀 다운로드(10p)",
            description = """
                    근거: 9/27 회의 항목 10 — "엑셀다운로드·페이지네이션 필수".

                    ★조회는 페이지로 주지만 **파일은 전량**이 나간다. 한 페이지만 받으면
                    담당자는 페이지를 넘겨가며 여러 파일을 합쳐야 한다.""")
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> export(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long productId,
            @RequestParam(required = false) java.util.List<Long> productIds,
            @RequestParam(required = false) Long warehouseId,
            @RequestParam(required = false) java.util.List<Long> warehouseIds) {
        java.util.List<com.daesung.sales.common.excel.ExcelExportUtil.Col> cols = java.util.List.of(
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("폐기일자", "date"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("전표번호", "refNo"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("창고", "warehouseName"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("분류코드", "catCode"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("분류명", "catName"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("도서코드", "bookCode"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("도서명", "bookName"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("폐기수량", "qty"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("폐기내역", cols,
                inventoryService.disposalsAll(fromDate, toDate,
                        MultiSelect.merge(productId, productIds),
                        MultiSelect.merge(warehouseId, warehouseIds)),
                com.daesung.sales.common.excel.ExcelExportUtil.Heading.period("폐기내역", fromDate, toDate));
        return excel.asDownload(xlsx, "폐기내역.xlsx");
    }

    @Operation(summary = "폐기 분류명별 요약(10p)",
            description = """
                    분류(catCode) 단위로 폐기 건수·수량을 합산한다.
                    낱건은 `GET /disposals`(상세)가 준다 — 발주처가 요청한 "요약(전체)/상세" 두 축이다.

                    수량은 **양수**로 준다(원장에는 음수로 기록된다).

                    ★필터는 상세와 **같은 축**이다(도서·창고 다중선택). 한쪽만 다르면
                    창고 둘을 고른 순간 요약과 상세의 수량이 갈리고, 그러면 둘 다 못 믿는다.""")
    @GetMapping("/summary")
    public ApiResponse<com.daesung.sales.inventory.dto.DisposalSummaryResponse> summary(
            @Parameter(description = "폐기일 시작(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "폐기일 종료(yyyy-MM-dd). 미지정=전체") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "도서(상품) id 필터(단건)") @RequestParam(required = false) Long productId,
            @Parameter(description = "도서(상품) id **다중선택**") @RequestParam(required = false)
            java.util.List<Long> productIds,
            @Parameter(description = "창고 id 필터(단건)") @RequestParam(required = false) Long warehouseId,
            @Parameter(description = "창고 id **다중선택**") @RequestParam(required = false)
            java.util.List<Long> warehouseIds) {
        return ApiResponse.success(inventoryService.disposalSummary(fromDate, toDate,
                MultiSelect.merge(productId, productIds),
                MultiSelect.merge(warehouseId, warehouseIds)));
    }
    @Operation(summary = "폐기 취소(역분개)",
            description = """
                    폐기 전표를 통째로 되돌린다. **물리 삭제가 아니다** —
                    반대 부호 이벤트를 새로 적어 상쇄하고 취소 이력을 남긴다.
                    재고는 이벤트 로그가 유일 진실이라, 지우면 "언제 왜 되돌렸나"가 사라진다.

                    · 같은 전표를 두 번 취소하면 재고가 반대로 밀린다 → **두 번째는 400**.
                    · **마감된 달은 막는다**(PERIOD_LOCKED) — 되돌리면 그 달 숫자가 바뀐다.
                    · `reversed`가 0일 수 있다. 재고 미관리 상품만 있던 전표라는 뜻이고 오류가 아니다.""")
    @PostMapping("/{refNo}/cancel")
    public ApiResponse<com.daesung.sales.inventory.dto.VoucherCancelResponse> cancel(
            @Parameter(description = "전표번호", required = true) @PathVariable String refNo,
            @Parameter(description = "취소 사유") @RequestParam(required = false) String reason) {
        return ApiResponse.success(inventoryService.cancelVoucher(refNo, reason));
    }


    @Operation(summary = "폐기수량 일괄 등록(엑셀 업로드, 10p)",
            description = """
                    근거: 9/27 회의 항목 8 — "폐기수량 일일이 적어야되는데 **일괄 등록 가능하게**.
                    엑셀업로드(**수불부 상품코드 + 수량**) 적용."

                    양식은 두 칸이면 된다. 열 순서가 달라도, 모르는 열이 붙어 있어도 읽는다.
                    ```
                    상품코드 | 수량 | (비고)
                    ```

                    · ★**한 건이라도 오류면 아무것도 등록되지 않는다.** 폐기는 재고를 깎는 전표라
                      절반만 들어가면 무엇이 빠졌는지 파일과 대조해야 한다.
                      오류 줄은 응답 `lines` 에 엑셀 행번호와 함께 나온다.
                    · 같은 상품이 여러 줄에 있으면 **합산**해 한 전표로 등록한다.
                    · `dryRun=true` 로 먼저 검증만 해볼 수 있다.
                    · ‼️양식에 **사유 칸은 없다**(9/27 A-3, 항목 9). 비고는 읽는다.""")
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<com.daesung.sales.inventory.dto.DisposalUploadResponse> upload(
            @Parameter(description = "엑셀 파일(xlsx)", required = true)
            @RequestPart("file") org.springframework.web.multipart.MultipartFile file,
            @Parameter(description = "처리일자(yyyy-MM-dd)", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate processedDate,
            @Parameter(description = "폐기 창고 id", required = true) @RequestParam Long warehouseId,
            @Parameter(description = "검증만 하고 등록하지 않음") @RequestParam(required = false,
                    defaultValue = "false") boolean dryRun) {
        return ApiResponse.success(
                disposalUploadService.upload(file, processedDate, warehouseId, dryRun));
    }
}
