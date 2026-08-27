package com.daesung.sales.partner.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.dto.CollateralExpiryResponse;
import com.daesung.sales.partner.dto.PartnerCreateRequest;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import com.daesung.sales.partner.dto.PartnerResponse;
import com.daesung.sales.partner.dto.PartnerUpdateRequest;
import com.daesung.sales.partner.service.PartnerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 거래처 관리. 실제 경로: /api/v1/masters/clients. */
@Tag(name = "기초관리 · 거래처", description = "거래처 마스터 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/clients")
public class PartnerController {

    private final PartnerService partnerService;
    private final ExcelExportUtil excel;

    @Operation(summary = "거래처 목록 조회",
            description = """
                    keyword(코드/명 부분일치)로 검색, 페이징·정렬 지원.
                    **기본은 거래중인 거래처만** 보여준다(만료일이 없는 건).
                    includeExpired=true면 만료된 거래처까지 포함 — 화면의 '만료된 거래처 포함' 체크박스.""")
    @GetMapping
    public ApiResponse<PageResponse<PartnerResponse>> list(
            @Parameter(description = "검색어(거래처코드 또는 거래처명 부분일치)") @RequestParam(required = false) String keyword,
            @Parameter(description = "만료된 거래처 포함(기본 false)")
            @RequestParam(required = false, defaultValue = "false") boolean includeExpired,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(partnerService.findAll(keyword, includeExpired, pageReq.toPageable()));
    }

    @Operation(summary = "거래처 목록 엑셀 다운로드", description = "30p 거래처관리 컬럼 전체. 검색조건 그대로.")
    @GetMapping("/export")
    public ResponseEntity<byte[]> listExport(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean includeExpired) {
        List<Col> cols = List.of(
                new Col("거래처코드", "code"), new Col("거래처명", "name"), new Col("신고명", "name1"),
                new Col("사업자번호", "bizNo"), new Col("업종", "bizStatus"), new Col("업태", "bizItem"),
                new Col("사업자명", "bossName"), new Col("사업자주민번호", "bossId"),
                new Col("연락처1", "tel1"), new Col("연락처2", "tel2"),
                new Col("휴대폰번호", "cellPhone"), new Col("팩스번호", "fax"),
                new Col("이메일1", "email1"), new Col("이메일2", "email2"),
                new Col("우편번호", "zip"), new Col("기본주소", "addr1"), new Col("상세주소", "addr2"),
                new Col("등록일", "startDate"), new Col("만료일", "endDate"),
                new Col("도시명", "cityName"), new Col("지역", "region"), new Col("관할지역", "zone2"),
                new Col("거래처구분", "clientCategory"),
                new Col("담보금액", "assureAmount"), new Col("담보만기", "assureExpiry"),
                new Col("담보내용", "assureNote"));
        byte[] xlsx = excel.toXlsx("거래처목록", cols,
                partnerService.findAll(keyword, includeExpired, PageRequest.of(0, 100000)).getContent());
        return excel.asDownload(xlsx, "거래처목록.xlsx");
    }

    @Operation(summary = "담보만기 알림 엑셀 다운로드", description = "만기 임박/만료 거래처(남은일수·상태).")
    @GetMapping("/collateral-expiry/export")
    public ResponseEntity<byte[]> collateralExpiryExport(
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(
                    iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE) java.time.LocalDate asOf,
            @RequestParam(defaultValue = "30") int withinDays) {
        List<Col> cols = List.of(
                new Col("거래처코드", "code"), new Col("거래처명", "name"),
                new Col("담보만기일", "assureExpiry"), new Col("담보금액", "assureAmount"),
                new Col("남은일수", "daysUntilExpiry"), new Col("상태", "status"));
        byte[] xlsx = excel.toXlsx("담보만기", cols, partnerService.collateralExpiry(asOf, withinDays).rows(),
                com.daesung.sales.common.excel.ExcelExportUtil.Heading.asOf("담보 만기 임박", asOf));
        return excel.asDownload(xlsx, "담보만기알림.xlsx");
    }

    @Operation(summary = "담보 만기 알림",
            description = "기준일(asOf, 미지정=오늘) 대비 담보 만기일이 withinDays(기본 30) 이내이거나 이미 만료된 "
                    + "거래처 목록. 만기일 오름차순 + 남은 일수 + 상태(EXPIRED/IMMINENT). 만기 1개월 전 팝업용.")
    @GetMapping("/collateral-expiry")
    public ApiResponse<CollateralExpiryResponse> collateralExpiry(
            @Parameter(description = "기준일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate asOf,
            @Parameter(description = "임박 판정 일수(기본 30)") @RequestParam(defaultValue = "30") int withinDays) {
        return ApiResponse.success(partnerService.collateralExpiry(asOf, withinDays));
    }

    @Operation(summary = "거래처 상세 조회", description = "id로 단건 조회. 없으면 404")
    @GetMapping("/{id}")
    public ApiResponse<PartnerResponse> get(@PathVariable Long id) {
        return ApiResponse.success(partnerService.findById(id));
    }

    @Operation(summary = "거래처 등록", description = "거래처코드 중복 시 400 반환")
    @PostMapping
    public ApiResponse<PartnerResponse> create(@Valid @RequestBody PartnerCreateRequest req) {
        return ApiResponse.success(partnerService.create(req));
    }

    @Operation(summary = "거래처 수정", description = "코드는 불변. 없으면 404")
    @PutMapping("/{id}")
    public ApiResponse<PartnerResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody PartnerUpdateRequest req) {
        return ApiResponse.success(partnerService.update(id, req));
    }
}
