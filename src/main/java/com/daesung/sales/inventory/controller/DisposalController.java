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

    @Operation(summary = "전표 삭제(마감 前)",
            description = """
                    **잘못 입력한 전표를 없던 것으로** 만든다(발주처 2026-08-14 [4] "마감 확정 前 삭제 가능").

                    ★취소와 다른 축이다 — 취소는 "되돌렸다"를 반대 이벤트로 장부에 남기고,
                    삭제는 애초에 없던 일로 만든다(원 이벤트를 무효화하고 잔량만 되돌린다).

                    · **사유 필수.** 지운 품목·수량은 상태변경 이력에 남는다.
                    · **이미 취소된 전표는 400** — 되돌린 기록이 장부에 선 뒤라 오입력이 아니다.
                    · **마감된 달은 400**(PERIOD_LOCKED).

                    ⚠️{@code inventory_txn} 행은 남는다(논리삭제). 재고의 유일 진실이라
                    물리삭제하면 수불부·채권이 파생되는 원장을 찢는 것과 같다.""")
    @DeleteMapping("/{refNo}")
    public ApiResponse<Void> deleteVoucher(
            @Parameter(description = "전표번호", required = true) @PathVariable String refNo,
            @Valid @RequestBody com.daesung.sales.logistics.dto.RevertRequest req) {
        inventoryService.deleteVoucher(refNo, req.reason(), currentAuditor.username());
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
    public ApiResponse<java.util.List<com.daesung.sales.inventory.dto.DisposalRecordRow>> list(
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
            java.util.List<Long> warehouseIds) {
        return ApiResponse.success(inventoryService.disposals(fromDate, toDate,
                MultiSelect.merge(productId, productIds),
                MultiSelect.merge(warehouseId, warehouseIds)));
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

}
