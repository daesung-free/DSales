package com.daesung.sales.closing.controller;

import com.daesung.sales.closing.dto.PeriodLockRequest;
import com.daesung.sales.closing.dto.PeriodLockResponse;
import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 마감관리 · 월마감. 실제 경로: /api/v1/closing/periods. 근거: 요구사항정의서 3대 로직/DB-30. */
@Tag(name = "마감관리 · 월마감", description = "월 단위 재무 마감(잠금)/해제 + 현황. 마감 월의 매출·수금 쓰기 차단")
@RestController
@RequiredArgsConstructor
@RequestMapping("/closing/periods")
public class PeriodLockController {

    private final PeriodLockService periodLockService;

    @Operation(summary = "월마감(잠금)",
            description = "해당 연·월을 마감. 이후 그 달의 매출 등록/취소·위탁정산·수금 등록이 PERIOD_LOCKED로 차단됨.")
    @PostMapping("/lock")
    public ApiResponse<PeriodLockResponse> lock(@Valid @RequestBody PeriodLockRequest req) {
        return ApiResponse.success(periodLockService.lock(req.year(), req.month(), req.memo()));
    }

    @Operation(summary = "월마감 해제(재오픈)",
            description = "마감된 월을 다시 열어 재무 쓰기를 허용. (해제 권한 정책은 발주처 확인 대상)")
    @PostMapping("/unlock")
    public ApiResponse<PeriodLockResponse> unlock(@Valid @RequestBody PeriodLockRequest req) {
        return ApiResponse.success(periodLockService.unlock(req.year(), req.month(), req.memo()));
    }

    @Operation(summary = "월마감 현황 조회", description = "연도별 월 마감 상태 목록.")
    @GetMapping
    public ApiResponse<List<PeriodLockResponse>> list(
            @Parameter(description = "연도", example = "2026") @RequestParam int year) {
        return ApiResponse.success(periodLockService.list(year));
    }
}
