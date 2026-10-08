package com.daesung.sales.closing.controller;

import com.daesung.sales.closing.service.PeriodLockCache;
import com.daesung.sales.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 월 잠금 여부만 돌려주는 조회(2026-10-08 [C]18).
 *
 * <p>근거: 프론트 회신 — 「물류 역할도 월마감 상태를 읽어야 한다. 지금은 입고·폐기 화면이
 * 마감된 달을 미리 알려 주지 못한다」. 마감된 달에 등록을 시도하면 저장 단계에서야
 * {@code PERIOD_LOCKED} 가 떠서, 담당자는 다 입력한 뒤에 막힌다.
 *
 * <p>★<b>마감 화면({@code /closing/periods})과 경로를 갈라 둔다.</b> 그 화면은 재무 전용이고
 * 마감 메모·처리자·물류작업비 동결 같은 <b>재무 정보가 함께 실린다</b>. 물류가 필요한 것은
 * "이 달이 잠겼나" 한 비트뿐이라, 화면을 열어 주는 대신 그 한 비트만 내리는 경로를 둔다.
 * (권한은 표에서 읽으므로 {@code /closing/**} 보다 앞서는 화면 한 줄을 추가해 전 역할 조회로 열었다.)
 */
@Tag(name = "마감 상태(전 역할 조회)", description = "월 잠금 여부만. 화면이 저장 전에 미리 알려 주기 위한 것")
@RestController
@RequestMapping("/closing/period-status")
@RequiredArgsConstructor
public class PeriodStatusController {

    private final PeriodLockCache periodLockCache;

    @Operation(summary = "월 잠금 여부",
            description = """
                    그 연도 1~12월의 **잠금 여부만** 돌려준다. 마감 메모·처리자·금액은 없다.

                    · 입고·폐기·매출 화면이 **저장 전에** 잠긴 달을 막거나 경고하는 데 쓴다.
                      지금은 다 입력한 뒤 저장 단계에서야 `PERIOD_LOCKED` 를 받는다.
                    · 날짜 하나로 묻고 싶으면 `date` 를 쓴다(그 날이 속한 달만 돌려준다).
                    · **전 역할이 조회할 수 있다** — 잠금 여부는 재무 정보가 아니라 입력 가능 여부다.
                      마감을 **걸고 푸는 것**은 그대로 재무·관리자 권한이다(`/closing/periods/lock`).""")
    @GetMapping
    public ApiResponse<List<Row>> status(
            @Parameter(description = "연도. date 를 주면 무시된다", example = "2026")
            @RequestParam(required = false) Integer year,
            @Parameter(description = "이 날이 속한 달만 조회(yyyy-MM-dd)")
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE)
            LocalDate date) {
        if (date != null) {
            return ApiResponse.success(List.of(row(date.getYear(), date.getMonthValue())));
        }
        int y = (year != null) ? year : LocalDate.now().getYear();
        List<Row> rows = new ArrayList<>(12);
        for (int m = 1; m <= 12; m++) {
            rows.add(row(y, m));
        }
        return ApiResponse.success(rows);
    }

    private Row row(int year, int month) {
        return new Row(year, month, periodLockCache.isLocked(year, month));
    }

    @Schema(name = "PeriodStatusRow", description = "월 잠금 여부")
    public record Row(
            @Schema(description = "연도") int year,
            @Schema(description = "월") int month,
            @Schema(description = "잠겼으면 true — 그 달로는 등록·수정이 안 된다") boolean locked
    ) {
    }
}
