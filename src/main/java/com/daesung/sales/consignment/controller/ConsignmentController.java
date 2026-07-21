package com.daesung.sales.consignment.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.consignment.dto.ConsignPendingResponse;
import com.daesung.sales.consignment.dto.ConsignSettleRequest;
import com.daesung.sales.consignment.dto.ConsignSettleResponse;
import com.daesung.sales.consignment.dto.ConsignmentOutRequest;
import com.daesung.sales.consignment.dto.ConsignmentOutResponse;
import com.daesung.sales.consignment.service.ConsignmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
            description = "미결을 부분/전량 정산 → 매출(위탁매출) 확정. 재고는 위탁출고 시 이미 반영되어 변동 없음. "
                    + "정산수량이 미결 잔여를 초과하면 409(OVER_SETTLEMENT).")
    @PostMapping("/settle")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<ConsignSettleResponse> settle(@Valid @RequestBody ConsignSettleRequest req) {
        return ApiResponse.success(consignmentService.settle(req));
    }
}
