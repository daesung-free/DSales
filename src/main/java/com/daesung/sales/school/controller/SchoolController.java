package com.daesung.sales.school.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.school.dto.SchoolCreateRequest;
import com.daesung.sales.school.dto.SchoolResponse;
import com.daesung.sales.school.dto.SchoolSyncResult;
import com.daesung.sales.school.dto.SchoolUpdateRequest;
import com.daesung.sales.school.service.SchoolService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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

/** 기초관리 - 학교관리(35p). 실제 경로: /api/v1/masters/schools. DSRE '가져오기'=보존형 동기화(POST /sync). */
@Tag(name = "기초관리 · 학교", description = "학교/학원 마스터 관리(35p)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/schools")
public class SchoolController {

    private final SchoolService schoolService;
    private final ExcelExportUtil excel;

    @Operation(summary = "학교 목록 조회",
            description = """
                    키워드·학교/학원 구분으로 검색. 페이징·정렬 지원.

                    · **키워드는 학교코드·학교명에 더해 거래처코드·거래처명까지** 훑는다.
                      예전엔 학교 쪽만 봐서, 엑셀엔 거래처코드 컬럼을 내려주면서
                      그 값으로 검색하면 0건이었다.
                    · `schoolType`으로 학교/학원을 가른다. 미지정이면 전체다.""")
    @GetMapping
    public ApiResponse<PageResponse<SchoolResponse>> list(
            @Parameter(description = "검색어 — 학교코드·학교명·거래처코드·거래처명 부분일치")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "학교/학원 구분 SCHOOL/HAKWON. 미지정=전체")
            @RequestParam(required = false) com.daesung.sales.school.entity.SchoolType schoolType,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(schoolService.findAll(keyword, schoolType, pageReq.toPageable()));
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

    @Operation(summary = "학교 등록", description = "식별키=거래처코드+학교코드 조합. 거래처구분·학교/학원구분은 직접입력(동기화 미대상).")
    @PostMapping
    public ApiResponse<SchoolResponse> create(@Valid @RequestBody SchoolCreateRequest req) {
        return ApiResponse.success(schoolService.create(req));
    }

    @Operation(summary = "학교 수정", description = "학교코드는 불변.")
    @PutMapping("/{id}")
    public ApiResponse<SchoolResponse> update(@PathVariable Long id, @Valid @RequestBody SchoolUpdateRequest req) {
        return ApiResponse.success(schoolService.update(id, req));
    }

    @Operation(summary = "DSRE 가져오기(동기화)",
            description = """
                    DSRE2 지사↔학교/학원 매핑을 읽어 **보존형으로 병합**한다(전체삭제 후 재수입 아님).
                    매칭키 = 거래처코드 + 학교코드.
                    · 있으면 → DSRE 관리 필드(거래처명·도시·지역·학교명·학교Y/N)만 갱신
                    · 없으면 → 신규 추가
                    · DSRE2에서 사라진 건 → 삭제하지 않고 미사용(active=false) 처리
                    · 매출프로그램 전용 데이터(수기 등록)는 건드리지 않음
                    거래처구분·학교/학원구분·메모는 수기 입력값이라 동기화가 덮어쓰지 않는다.
                    DSRE 연동(daesung.dsre.enabled=true) 필요.""")
    @PostMapping("/sync")
    public ApiResponse<SchoolSyncResult> sync() {
        return ApiResponse.success(schoolService.syncFromDsre());
    }

    @Operation(summary = "학교/학원검색(29p)",
            description = """
                    레거시 `학교검색.vb`와 같은 필터 4종·같은 컬럼으로 조회한다.
                    필터는 전부 **부분일치**이고, 빈 값은 조건에서 빠진다.

                    ★**특약점명은 대표·모의고사·IC 세 축을 다 뒤진다** —
                    같은 학교라도 상품군에 따라 담당 특약점이 달라, 대표만 보면 못 찾는다
                    (레거시 원문: `custName like ? or mCustName like ? or iCustName like ?`).

                    ‼️레거시는 특약점(모의고사)·특약점(IC) 컬럼을 **주석 처리해 화면에 안 띄운다**.
                    여기서는 응답에 담아 둔다 — 화면 노출 여부는 발주처가 정할 일이고,
                    빼 두면 필요해질 때 서버부터 다시 고쳐야 한다.

                    화면명은 '학교검색' → '**학교/학원검색**'으로 확정(발주처 2026-08-31).""")
    @GetMapping("/search")
    public ApiResponse<java.util.List<com.daesung.sales.school.dto.SchoolSearchRow>> search(
            @Parameter(description = "학교코드(부분일치)") @RequestParam(required = false) String schoolCode,
            @Parameter(description = "학교/학원명(부분일치)") @RequestParam(required = false) String schoolName,
            @Parameter(description = "지역(지역코드·지역명·관할 어디든 부분일치)")
            @RequestParam(required = false) String region,
            @Parameter(description = "특약점명(대표·모의고사·IC 세 축 부분일치)")
            @RequestParam(required = false) String partnerName) {
        return ApiResponse.success(schoolService.search(schoolCode, schoolName, region, partnerName));
    }

    @Operation(summary = "학교/학원검색 엑셀 다운로드",
            description = "조회와 같은 조건. 특약점 3축(대표·모의고사·IC)까지 담는다.")
    @GetMapping("/search/export")
    public org.springframework.http.ResponseEntity<byte[]> searchExport(
            @RequestParam(required = false) String schoolCode,
            @RequestParam(required = false) String schoolName,
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String partnerName) {
        java.util.List<Col> cols = java.util.List.of(
                new Col("학교코드", "schoolCode"), new Col("학교/학원명", "schoolName"),
                new Col("지역코드", "cityCode"), new Col("지역명", "cityName"),
                new Col("관할", "partnerLoc"), new Col("특약점", "partnerName"),
                new Col("관할명", "partnerLocName"),
                new Col("특약점(모의고사)", "mockPartnerName"),
                new Col("특약점(IC)", "icPartnerName"),
                new Col("거래처구분", "clientCategory"));
        byte[] xlsx = excel.toXlsx("학교학원검색", cols,
                schoolService.search(schoolCode, schoolName, region, partnerName));
        return excel.asDownload(xlsx, "학교학원검색.xlsx");
    }
}
