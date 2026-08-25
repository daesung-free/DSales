package com.daesung.sales.logistics.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.logistics.dto.LogisCostManualRequest;
import com.daesung.sales.logistics.dto.LogisCostManualResponse;
import com.daesung.sales.logistics.service.LogisCostManualService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 물류 작업비 <b>수기 등록</b>(28p 에디팅 모드). 실제 경로: /api/v1/logistics-costs/manual.
 * 근거: 발주처 회신 2026-08-21 — "행 우클릭으로 물류비를 수기 등록·수정·삭제·복사".
 *
 * <p>★DSRE 연동 조건을 걸지 않는다. 수기 입력은 DSRE2를 보지 않으므로
 * 연동이 꺼져 있어도 되어야 한다(자동계산 조회만 연동에 묶인다).
 */
@Tag(name = "물류 · 작업비 수기등록",
        description = "28p 에디팅 모드 — 수기 물류비 등록·수정·삭제·복사. 자동계산분과 합쳐져 소계·합계에 잡힌다")
@RestController
@RequiredArgsConstructor
@RequestMapping("/logistics-costs/manual")
public class LogisCostManualController {

    private final LogisCostManualService manualService;

    @Operation(summary = "수기 물류작업비 목록",
            description = "접수일자 기간의 수기 등록분만. 자동계산분과 합쳐진 화면은 `/logistics-costs/outbound/detail`.")
    @GetMapping
    public ApiResponse<List<LogisCostManualResponse>> list(
            @Parameter(description = "시작일", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일", required = true) @RequestParam
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(manualService.search(fromDate, toDate));
    }

    @Operation(summary = "수기 물류작업비 등록",
            description = """
                    화면에서 직접 넣는 물류비 행. **자동계산 로직은 건드리지 않는다** —
                    별도 표에 담고 조회 시 합칠 뿐이라, 자동계산분이 어떻게 바뀌든 이 값은 그대로다.

                    · 접수일자가 귀속 축이다(자동계산분과 같다). 그래야 한 화면에서 합쳐진다.
                    · **마감된 달에는 넣을 수 없다**(PERIOD_LOCKED) — 막지 않으면
                      과거 작업비 고정 원칙이 수기 행으로 뚫린다.
                    · 소계·누계·합계·총계에 그대로 잡힌다(같은 타입으로 합쳐지므로).""")
    @PostMapping
    public ApiResponse<LogisCostManualResponse> create(@Valid @RequestBody LogisCostManualRequest req) {
        return ApiResponse.success(manualService.create(req));
    }

    @Operation(summary = "수기 물류작업비 수정",
            description = """
                    **접수일자·신청번호·시행코드는 무시된다** — 귀속 축이라 바꾸면 다른 달로 옮겨진다.
                    옮기려면 지우고 다시 넣는다(그래야 원래 달에서 사라진 이유가 삭제 기록으로 남는다).""")
    @PutMapping("/{id}")
    public ApiResponse<LogisCostManualResponse> update(@PathVariable Long id,
                                                       @Valid @RequestBody LogisCostManualRequest req) {
        return ApiResponse.success(manualService.update(id, req));
    }

    @Operation(summary = "수기 물류작업비 복사",
            description = "같은 내용으로 새 행을 만든다. 비슷한 건을 반복 입력하는 화면이라 붙인 기능이다.")
    @PostMapping("/{id}/copy")
    public ApiResponse<LogisCostManualResponse> copy(@PathVariable Long id) {
        return ApiResponse.success(manualService.copy(id));
    }

    @Operation(summary = "수기 물류작업비 삭제",
            description = "논리삭제 — 행은 남고 삭제자·시각이 기록된다. 지운 금액이 왜 사라졌는지 남아야 한다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        manualService.delete(id);
        return ApiResponse.success(null);
    }
}
