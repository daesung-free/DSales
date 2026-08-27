package com.daesung.sales.consignment.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.consignment.dto.SettlementDraftRequest;
import com.daesung.sales.consignment.dto.SettlementDraftResponse;
import com.daesung.sales.consignment.service.SettlementDraftService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매출등록 · 위탁정산 <b>임시저장</b>(13p 1단계).
 *
 * <p>경로가 {@code /consignment}가 아니라 {@code /sales/settlement}인 이유 —
 * 화면이 이미 이 경로로 부르고 있다(`features/sales/api.ts`). 임시저장은
 * <b>매출등록 화면의 기능</b>이지 위탁 원장 조작이 아니라, 화면 쪽 분류가 더 맞다.
 */
@Tag(name = "매출관리 · 위탁정산 임시저장",
        description = "13p 2단계 중 1단계. **매출 미반영** — 미결·재고·매출 어느 것도 바뀌지 않는다")
@RestController
@RequestMapping("/sales/settlement")
@RequiredArgsConstructor
public class SettlementDraftController {

    private final SettlementDraftService settlementDraftService;

    @Operation(summary = "위탁정산 임시저장",
            description = """
                    정산 입력값을 초안으로 저장한다. **아무것도 확정하지 않는다** —
                    미결 잔여도, 재고도, 매출도 그대로다.
                    정가·공급률을 안 보내면 도서 마스터·거래처별 단가에서 자동으로 채운다.
                    저장 시점에도 초과정산은 거른다(확정까지 갔다가 거절당하지 않게).""")
    @PostMapping("/draft")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<SettlementDraftResponse> save(@Valid @RequestBody SettlementDraftRequest req) {
        return ApiResponse.success(settlementDraftService.save(req));
    }

    @Operation(summary = "임시저장 목록",
            description = "최근 저장순. 전부 매출 미반영 상태다. 행마다 저장 시점의 미결 잔여를 함께 낸다.")
    @GetMapping("/drafts")
    public ApiResponse<List<SettlementDraftResponse>> list() {
        return ApiResponse.success(settlementDraftService.list());
    }

    @Operation(summary = "임시저장 삭제",
            description = "논리삭제(누가·언제 지웠는지 남는다). 확정에 쓰인 초안은 확정 시 자동 정리된다.")
    @DeleteMapping("/draft/{draftId}")
    public ApiResponse<Void> delete(
            @Parameter(description = "초안번호", example = "DRAFT-20260622-1")
            @PathVariable String draftId) {
        settlementDraftService.delete(draftId);
        return ApiResponse.success(null);
    }
}
