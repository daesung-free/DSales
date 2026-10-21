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
    public ApiResponse<com.daesung.sales.common.response.PageResponse<
            com.daesung.sales.school.dto.SchoolSearchRow>> search(
            @Parameter(description = "학교코드(부분일치)") @RequestParam(required = false) String schoolCode,
            @Parameter(description = "학교/학원명(부분일치)") @RequestParam(required = false) String schoolName,
            @Parameter(description = "지역(지역코드·지역명·관할 어디든 부분일치)")
            @RequestParam(required = false) String region,
            @Parameter(description = "특약점명(대표·모의고사·IC 세 축 부분일치)")
            @RequestParam(required = false) String partnerName,
            @org.springdoc.core.annotations.ParameterObject
            com.daesung.sales.common.dto.PageRequestDto pageReq) {
        // ★학교 줄만 있는 평평한 목록이라 **줄 단위로 잘라도 안전**하다
        //   (응시현황처럼 소계 줄이 섞여 있지 않다 — 프론트 확인 2026-10-21).
        //   전 건이 4,860개 · 1.2MB 라 당장 급하진 않았지만, 학교가 늘면 같은 길을 간다.
        var all = schoolService.search(schoolCode, schoolName, region, partnerName);
        var pageable = pageReq.toPageable();
        int from = (int) Math.min(pageable.getOffset(), all.size());
        int to = Math.min(from + pageable.getPageSize(), all.size());
        return ApiResponse.success(com.daesung.sales.common.response.PageResponse.of(
                new org.springframework.data.domain.PageImpl<>(
                        all.subList(from, to), pageable, all.size())));
    }

    @Operation(summary = "판매채널 목록",
            description = """
                    매출·출고조회의 **판매채널 콤보**에 넣을 값 목록.

                    ★<b>코드표가 아니다.</b> 등록된 학교들의 판매채널 값을 모아서 만든다 —
                    레거시가 똑같이 한다({@code CommonDB.vb:475}
                    `SELECT type1 FROM schDataDT GROUP BY type1`). 고정 목록으로 두면
                    채널이 하나 늘 때마다 배포해야 한다.

                    · 값은 **학교 마스터**에 있다(레거시 `schDataDT.type1`). 거래처가 아니라
                      그 학교가 어느 채널로 들어왔는지다.
                    · 화면은 여기에 `< 전체 >`·`< 미분류 >` 를 앞에 붙여 쓴다(레거시와 같다).
                      조회 시 `salesChannels=미분류` 를 보내면 채널이 비어 있는 학교만 걸린다.""")
    @GetMapping("/sales-channels")
    public ApiResponse<java.util.List<String>> salesChannels() {
        return ApiResponse.success(schoolService.salesChannels());
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
                // 발주처 1차회신 #18 확정 7컬럼이 앞에 온다 —
                // 학교코드·학교명·지역코드·지역명·도시명·거래처명1·거래처명2.
                // ‼️예전엔 '지역명' 라벨에 **도시명 값**(cityName)이 들어가 있었다.
                //   둘은 다른 축이라(지역=서울관할 / 도시=강남) 라벨만 보고 쓰면 틀린 값을 옮긴다.
                new Col("학교코드", "schoolCode"), new Col("학교/학원명", "schoolName"),
                new Col("지역코드", "cityCode"), new Col("지역명", "regionName"),
                new Col("도시명", "cityName"),
                new Col("거래처명1", "partnerName1"), new Col("거래처명2", "partnerName2"),
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
