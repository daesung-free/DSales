package com.daesung.sales.receivable.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.receivable.dto.ArStatusResponse;
import com.daesung.sales.receivable.dto.CarryforwardResult;
import com.daesung.sales.receivable.dto.CollectionRequest;
import com.daesung.sales.receivable.dto.CollectionResponse;
import com.daesung.sales.receivable.service.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 마감관리 · 채권/수금. 실제 경로: /api/v1/closing. 스펙 §마감관리(collections/ar-status) 대응. */
@Tag(name = "마감관리 · 채권/수금", description = "수금 등록/조회 · 이월 생성(idempotent) · 미수금 현황")
@RestController
@RequiredArgsConstructor
@RequestMapping("/closing")
public class ReceivableController {

    private final ReceivableService receivableService;

    @Operation(summary = "수금 등록",
            description = "수금번호(C) 채번. 유형=어음일 때만 어음정보(번호/만기/은행/지점) 저장. 채권 잔액에서 차감됨.")
    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CollectionResponse> registerCollection(@Valid @RequestBody CollectionRequest req) {
        return ApiResponse.success(receivableService.registerCollection(req));
    }

    @Operation(summary = "수금 목록 조회", description = "기간·거래처로 수금 내역 조회.")
    @GetMapping("/collections")
    public ApiResponse<PageResponse<CollectionResponse>> listCollections(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id") @RequestParam(required = false) Long partnerId,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(
                receivableService.searchCollections(fromDate, toDate, partnerId, pageReq.toPageable()));
    }

    @Operation(summary = "채권 이월 스냅샷 생성(idempotent)",
            description = "해당 연도 이월(=전년말 채권 잔액)을 계산해 저장. 기존 연도분 삭제 후 재생성. "
                    + "레거시의 '조회 시 자동생성' 부수효과를 제거한 명시 API.")
    @PostMapping("/carryforward")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CarryforwardResult> generateCarryforward(
            @Parameter(description = "이월 귀속 연도", example = "2026") @RequestParam int fiscalYear) {
        return ApiResponse.success(receivableService.generateCarryforward(fiscalYear));
    }

    @Operation(summary = "미수금(외상매출) 현황 조회",
            description = "거래처별 잔액 = 이월 + 기간 채권발생(매출+세액−반품) − 수금. 담보비율/경고등급 포함. "
                    + "기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/ar-status")
    public ApiResponse<ArStatusResponse> arStatus(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id 필터") @RequestParam(required = false) Long partnerId) {
        return ApiResponse.success(receivableService.arStatus(fromDate, toDate, partnerId));
    }
}
