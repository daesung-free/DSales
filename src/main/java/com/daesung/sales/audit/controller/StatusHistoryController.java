package com.daesung.sales.audit.controller;

import com.daesung.sales.audit.dto.StatusHistoryResponse;
import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.repository.StatusHistoryRepository;
import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 감사 - 상태변경 이력 조회. 실제 경로: /api/v1/audit/status-history. */
@Tag(name = "감사 · 상태변경 이력", description = "상태가 언제·누구에 의해·왜 바뀌었는지 추적(보안심사 대응)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/audit/status-history")
public class StatusHistoryController {

    private final StatusHistoryRepository repository;
    private final ExcelExportUtil excel;

    @Operation(summary = "상태변경 이력 조회",
            description = """
                    대상(종류·id) / 변경자 / 기간을 조합해 조회한다. 미지정 조건은 무시된다. 최신순.
                    · 대상 하나의 흐름: entityType + entityId
                    · 특정 담당자가 무엇을 바꿨나: changedBy + 기간
                    이력은 추가만 되고 수정·삭제되지 않는다.""")
    @GetMapping
    public ApiResponse<PageResponse<StatusHistoryResponse>> list(
            @Parameter(description = "대상 종류(SALE/PERIOD_LOCK/CONSIGNMENT_OUT/SCHOOL/APP_USER)")
            @RequestParam(required = false) StatusEntityType entityType,
            @Parameter(description = "대상 id") @RequestParam(required = false) Long entityId,
            @Parameter(description = "변경자(로그인 사용자명)") @RequestParam(required = false) String changedBy,
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(PageResponse.of(repository
                .search(entityType, entityId, blank(changedBy), start(fromDate), end(toDate), pageReq.toPageable())
                .map(StatusHistoryResponse::from)));
    }

    @Operation(summary = "상태변경 이력 엑셀 다운로드", description = "보안심사 제출용. 조회 조건 그대로.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) StatusEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<Col> cols = List.of(
                new Col("변경시각", "changedAt"), new Col("변경자", "changedBy"),
                new Col("대상종류", "entityType"), new Col("대상id", "entityId"),
                new Col("상태축", "field"), new Col("이전값", "fromStatus"), new Col("이후값", "toStatus"),
                new Col("사유", "reason"));
        byte[] xlsx = excel.toXlsx("상태변경이력", cols, repository
                .search(entityType, entityId, blank(changedBy), start(fromDate), end(toDate),
                        PageRequest.of(0, 100000))
                .map(StatusHistoryResponse::from).getContent());
        return excel.asDownload(xlsx, "상태변경이력.xlsx");
    }

    private static String blank(String s) {
        return (s == null || s.isBlank()) ? null : s.trim();
    }

    private static LocalDateTime start(LocalDate d) {
        return (d == null) ? null : d.atStartOfDay();
    }

    private static LocalDateTime end(LocalDate d) {
        return (d == null) ? null : d.atTime(LocalTime.MAX);
    }
}
