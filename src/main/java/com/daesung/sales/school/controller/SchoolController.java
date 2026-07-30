package com.daesung.sales.school.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.school.dto.SchoolCreateRequest;
import com.daesung.sales.school.dto.SchoolResponse;
import com.daesung.sales.school.dto.SchoolUpdateRequest;
import com.daesung.sales.school.service.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 학교관리(35p). 실제 경로: /api/v1/masters/schools. DSRE '가져오기'(동기화)는 후속(조건부). */
@Tag(name = "기초관리 · 학교", description = "학교/학원 마스터 관리(35p)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/schools")
public class SchoolController {

    private final SchoolService schoolService;
    private final ExcelExportUtil excel;

    @Operation(summary = "학교 목록 조회", description = "keyword(학교코드/명 부분일치)로 검색, 페이징·정렬 지원")
    @GetMapping
    public ApiResponse<PageResponse<SchoolResponse>> list(
            @Parameter(description = "검색어(학교코드 또는 학교명 부분일치)") @RequestParam(required = false) String keyword,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(schoolService.findAll(keyword, pageReq.toPageable()));
    }

    @Operation(summary = "학교 목록 엑셀 다운로드", description = "35p 컬럼(거래처코드·도시·지역·거래처명·학교코드·학교명·학교Y/N·구분·메모).")
    @GetMapping("/export")
    public ResponseEntity<byte[]> listExport(@RequestParam(required = false) String keyword) {
        List<Col> cols = List.of(
                new Col("거래처코드", "custCode"), new Col("도시", "city"), new Col("지역", "region"),
                new Col("거래처명", "custName"), new Col("학교코드", "schoolCode"), new Col("학교명", "schoolName"),
                new Col("학교YN", "isSchool"), new Col("거래처구분", "clientCategory"),
                new Col("학교학원구분", "schoolType"), new Col("메모", "memo"));
        byte[] xlsx = excel.toXlsx("학교목록", cols,
                schoolService.findAll(keyword, PageRequest.of(0, 100000)).getContent());
        return excel.asDownload(xlsx, "학교목록.xlsx");
    }

    @Operation(summary = "학교 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<SchoolResponse> get(@PathVariable Long id) {
        return ApiResponse.success(schoolService.findById(id));
    }

    @Operation(summary = "학교 등록", description = "학교코드=거래처코드 동일값. 거래처구분·학교/학원구분은 직접입력.")
    @PostMapping
    public ApiResponse<SchoolResponse> create(@Valid @RequestBody SchoolCreateRequest req) {
        return ApiResponse.success(schoolService.create(req));
    }

    @Operation(summary = "학교 수정", description = "학교코드는 불변.")
    @PutMapping("/{id}")
    public ApiResponse<SchoolResponse> update(@PathVariable Long id, @Valid @RequestBody SchoolUpdateRequest req) {
        return ApiResponse.success(schoolService.update(id, req));
    }
}
