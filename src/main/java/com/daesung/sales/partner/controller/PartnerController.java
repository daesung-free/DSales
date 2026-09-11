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

    @Operation(summary = "거래처 가져오기(DSRE2 동기화)",
            description = """
                    DSRE2 거래처 원본(`tbl_cust_info`)을 읽어 병합한다. **보존형**이다.

                    · 거래처코드가 있으면 → DSRE2가 관리하는 항목만 덮어쓴다
                      (상호·풀네임·도시·관할·사업자번호·대표자·업태/종목·연락처·이메일·주소)
                    · 없으면 → 신규 추가
                    · **담보(금액·만기·내용)·거래처구분·주민번호는 건드리지 않는다** —
                      우리 재무·영업이 넣는 값이라 날아가면 채권 화면이 비어 버린다
                    · DSRE2에 없는 우리 쪽 거래처는 **손대지 않는다**(수기 등록분일 수 있고,
                      거래처는 과거 매출이 전부 FK로 물려 있다)

                    ‼️학교 동기화가 `tbl_cust_info`를 조인하므로 **거래처를 먼저** 맞춰야
                    학교에 거래처명·도시가 붙는다. 순서는 거래처 → 학교다.
                    DSRE 연동(`daesung.dsre.enabled=true`) 필요.""")
    @PostMapping("/sync")
    public ApiResponse<com.daesung.sales.partner.dto.ClientSyncResult> sync() {
        return ApiResponse.success(partnerService.syncFromDsre());
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

    @Operation(summary = "코드값 목록(거래처 등록 화면 선택용)",
            description = """
                    거래처 등록·수정 화면의 **셀렉트 채우기**용. 한 번에 네 축을 준다.

                    | 축 | 성격 | 값 |
                    |---|---|---|
                    | `clientCategories` | **고정** | 특약점·기타학원·B2B·대성·자사몰 (정본 확정) |
                    | `regions` | 열림 | 쓰이는 값 + 건수. 새 값 입력 가능 |
                    | `zones` | 열림 | 관할지역. region과 **별개 축**이다(정본 4탭에 둘 다 있음) |

                    ### 고정과 열림을 왜 나누나
                    거래처구분은 정본이 5값으로 못 박았다 — 서버가 그 밖의 값을 **400으로 거부**한다.
                    지역은 정해진 목록이 없어 막을 수 없다. 대신 쓰이는 값을 보여줘 고르게 하고,
                    새 지역은 그대로 입력할 수 있게 둔다(분류코드와 같은 방식).

                    ### 읽는 법
                    `usedCount`가 1인 값이 비슷한 이름 옆에 있으면 **오타를 의심할 자리**다 —
                    `경남`과 `경상남도`가 섞이면 필터에서 한쪽이 통째로 빠진다.""")
    @GetMapping("/codes")
    public ApiResponse<java.util.Map<String, Object>> codes() {
        return ApiResponse.success(java.util.Map.of(
                "clientCategories", com.daesung.sales.common.code.MasterCodes.CLIENT_CATEGORY,
                "regions", partnerService.usedRegions(),
                "zones", partnerService.usedZones()));
    }

}
