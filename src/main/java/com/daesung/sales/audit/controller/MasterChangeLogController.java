package com.daesung.sales.audit.controller;

import com.daesung.sales.audit.dto.MasterChangeResponse;
import com.daesung.sales.audit.entity.MasterEntityType;
import com.daesung.sales.audit.service.MasterChangeLogService;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
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

/**
 * 기초정보 변경 이력 조회. 실제 경로: /api/v1/audit/master-changes.
 *
 * <p>근거: 발주처 확정(자료요청서 3-1 라) — "잘못 수정된 경우를 발견하기 위해
 * 기초정보(거래처·상품·단가) 변경 이력도 함께 포함".
 */
@Tag(name = "감사 · 기초정보 변경이력",
        description = "거래처·도서·거래처별단가가 언제·누구에 의해 무엇에서 무엇으로 바뀌었는지 추적")
@RestController
@RequestMapping("/audit/master-changes")
@RequiredArgsConstructor
public class MasterChangeLogController {

    private final MasterChangeLogService service;
    private final ExcelExportUtil excel;

    @Operation(summary = "기초정보 변경 이력 조회",
            description = """
                    거래처·도서·거래처별단가의 **수정 이력**을 최신순으로 조회한다.

                    · 한 행 = **필드 하나의 변경**. 저장 한 번에 세 항목이 바뀌면 세 행이 남는다.
                    · **바뀐 필드만** 기록된다 — 저장만 누르고 아무것도 안 바꾸면 이력이 생기지 않는다.
                    · 생성·삭제는 대상이 아니다(생성은 등록자·등록일시가, 비활성은 상태변경 이력이 답한다).
                    · 사업자주민번호는 이력에도 **마스킹**되어 남는다.""")
    @GetMapping
    public ApiResponse<PageResponse<MasterChangeResponse>> search(
            @Parameter(description = "대상 종류(미지정=전체)") @RequestParam(required = false)
            MasterEntityType entityType,
            @Parameter(description = "대상 id(미지정=전체)") @RequestParam(required = false) Long entityId,
            @Parameter(description = "변경자(미지정=전체)") @RequestParam(required = false) String changedBy,
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 해당일 포함)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(service.search(entityType, entityId, changedBy,
                fromDate, toDate, pageReq.toPageable()));
    }

    @Operation(summary = "기초정보 변경 이력 엑셀 다운로드")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) MasterEntityType entityType,
            @RequestParam(required = false) Long entityId,
            @RequestParam(required = false) String changedBy,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<Col> cols = List.of(
                new Col("변경시각", "changedAt"), new Col("변경자", "changedBy"),
                new Col("대상", "entityTypeName"), new Col("대상코드", "entityCode"),
                new Col("항목", "fieldLabel"), new Col("필드명", "field"),
                new Col("이전 값", "oldValue"), new Col("이후 값", "newValue"));
        byte[] xlsx = excel.toXlsx("기초정보변경이력", cols,
                service.list(entityType, entityId, changedBy, fromDate, toDate,
                        PageRequest.of(0, 100000)));
        return excel.asDownload(xlsx, "기초정보_변경이력.xlsx");
    }
}
