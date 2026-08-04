package com.daesung.sales.person.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.person.dto.PersonMemberRequest;
import com.daesung.sales.person.dto.PersonMemberResponse;
import com.daesung.sales.person.service.PersonMemberService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 개인회원관리(구 IC). 실제 경로: /api/v1/masters/person-members. */
@Tag(name = "기초관리 · 개인회원", description = "개인 결제 건의 수취인·배송지 관리(구 IC 개인회원관리)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/person-members")
public class PersonMemberController {

    private final PersonMemberService service;
    private final ExcelExportUtil excel;

    @Operation(summary = "개인회원 조회",
            description = """
                    레거시 개인회원관리 조회 조건 그대로 — 결제일 기간 + 학생ID/이름 + 상품명 + 연락처 부분일치.
                    미입력 조건은 무시된다. 정렬은 등록일 최신순, 같으면 학생ID 순.""")
    @GetMapping
    public ApiResponse<PageResponse<PersonMemberResponse>> list(
            @Parameter(description = "결제일 시작(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "결제일 종료(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "학생ID 또는 학생이름 부분일치") @RequestParam(required = false) String keyword,
            @Parameter(description = "상품명 부분일치") @RequestParam(required = false) String goods,
            @Parameter(description = "연락처 부분일치(연락처1·2 동시검색)") @RequestParam(required = false) String tel,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(service.search(fromDate, toDate, keyword, goods, tel, pageReq.toPageable()));
    }

    @Operation(summary = "개인회원 엑셀 다운로드", description = "조회 조건 그대로 엑셀로 내려받는다.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String goods,
            @RequestParam(required = false) String tel) {
        List<Col> cols = List.of(
                new Col("등록일", "inputDate"), new Col("결제일", "payDate"),
                new Col("학생ID", "studentId"), new Col("학생이름", "studentName"),
                new Col("상품코드", "goodsCode"), new Col("상품명", "goodsName"),
                new Col("우편번호", "post"), new Col("주소1", "addr1"), new Col("주소2", "addr2"),
                new Col("수취인", "receiver"), new Col("연락처1", "tel1"), new Col("연락처2", "tel2"),
                new Col("메모", "memo"), new Col("관리", "manager"));
        byte[] xlsx = excel.toXlsx("개인회원", cols,
                service.search(fromDate, toDate, keyword, goods, tel, PageRequest.of(0, 100000)).getContent());
        return excel.asDownload(xlsx, "개인회원.xlsx");
    }

    @Operation(summary = "개인회원 단건 조회")
    @GetMapping("/{id}")
    public ApiResponse<PersonMemberResponse> get(@PathVariable Long id) {
        return ApiResponse.success(service.findById(id));
    }

    @Operation(summary = "개인회원 등록")
    @PostMapping
    public ApiResponse<PersonMemberResponse> create(@Valid @RequestBody PersonMemberRequest req) {
        return ApiResponse.success(service.create(req));
    }

    @Operation(summary = "개인회원 수정")
    @PutMapping("/{id}")
    public ApiResponse<PersonMemberResponse> update(@PathVariable Long id,
                                                    @Valid @RequestBody PersonMemberRequest req) {
        return ApiResponse.success(service.update(id, req));
    }

    @Operation(summary = "개인회원 삭제", description = "논리삭제 — 행은 남고 삭제자·시각이 기록된다.")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        service.delete(id);
        return ApiResponse.success();
    }
}
