package com.daesung.sales.product.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.product.dto.PartnerSupplyRateBulkRequest;
import com.daesung.sales.product.dto.PartnerSupplyRateBulkResult;
import com.daesung.sales.product.dto.PartnerSupplyRateRequest;
import com.daesung.sales.product.dto.PartnerSupplyRateResponse;
import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.service.PartnerSupplyRateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
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

/**
 * 기초관리 - 거래처별 단가·노출 매핑(34p). 실제 경로: /api/v1/masters/partner-supply-rates.
 *
 * <p>화면은 도서관리의 세 번째 탭이지만 <b>데이터에 도서 차원이 없다</b> —
 * 정본 34p가 "거래처별로 상품군(대분류)마다"라고 못박고 있고, 예시도 대분류 단위다
 * ("특약점 D모의고사 75%, 교재 60%" → 회신 2026-08-20으로 'D모의고사'는 대분류 '기타고사'로 정정).
 * 그래서 경로를 도서 하위에 두지 않았다.
 */
@Tag(name = "기초관리 · 거래처별 단가",
        description = "거래처 × 대분류 공급률·할인액·Web게시·사용여부(34p). 매출등록 공급률 자동조회의 원천")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/partner-supply-rates")
public class PartnerSupplyRateController {

    private final PartnerSupplyRateService service;
    private final ExcelExportUtil excel;
    private final com.daesung.sales.product.service.ProductUploadService uploadService;

    @Operation(summary = "거래처별 공급률 목록",
            description = """
                    거래처구분·거래처·대분류로 좁혀 조회한다(전부 선택사항, 미지정=전체).

                    · **매핑이 없는 거래처는 도서 기본정보의 공급률이 바탕값**이 된다(정본 34p).
                      그래서 전 거래처×전 대분류를 미리 깔아둘 필요가 없다.
                    · 같은 거래처라도 대분류마다 다른 공급률을 둘 수 있다.""")
    @GetMapping
    public ApiResponse<List<PartnerSupplyRateResponse>> list(
            @Parameter(description = "거래처 id. 미지정=전체") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "대분류. 미지정=전체") @RequestParam(required = false) MajorCategory majorCategory,
            @Parameter(description = "거래처구분(특약점/기타학원/B2B/대성/자사몰). 미지정=전체")
            @RequestParam(required = false) String clientCategory) {
        return ApiResponse.success(service.search(partnerId, majorCategory, clientCategory));
    }

    @Operation(summary = "거래처별 공급률 엑셀 다운로드", description = "검색조건 전체를 xlsx로(34p 데이터 항목).")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(@RequestParam(required = false) Long partnerId,
                                         @RequestParam(required = false) MajorCategory majorCategory,
                                         @RequestParam(required = false) String clientCategory) {
        List<Col> cols = List.of(
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"),
                new Col("거래처구분", "clientCategory"), new Col("대분류", "majorCategoryName"),
                new Col("Web게시", "webVisible"), new Col("공급률", "supplyRate"),
                new Col("할인액", "discountAmount"), new Col("사용여부", "useYn"));
        byte[] xlsx = excel.toXlsx("거래처별단가", cols,
                service.search(partnerId, majorCategory, clientCategory));
        return excel.asDownload(xlsx, "거래처별단가.xlsx");
    }

    @Operation(summary = "거래처별 공급률 단건 조회", description = "없으면 404. 사용여부가 꺼진 매핑도 조회된다(관리용).")
    @GetMapping("/{partnerId}/{majorCategory}")
    public ApiResponse<PartnerSupplyRateResponse> get(@PathVariable Long partnerId,
                                                      @PathVariable MajorCategory majorCategory) {
        return ApiResponse.success(service.get(partnerId, majorCategory));
    }

    @Operation(summary = "거래처별 공급률 등록/수정",
            description = """
                    거래처 × 대분류 upsert(있으면 수정, 없으면 생성).
                    **보내지 않은 항목은 건드리지 않는다** — 공급률만 고치려다 Web게시가 꺼지면
                    그 거래처가 신청사이트에서 조용히 사라진다.""")
    @PutMapping("/{partnerId}/{majorCategory}")
    public ApiResponse<PartnerSupplyRateResponse> upsert(
            @PathVariable Long partnerId, @PathVariable MajorCategory majorCategory,
            @Valid @RequestBody PartnerSupplyRateRequest req) {
        return ApiResponse.success(service.upsert(partnerId, majorCategory, req));
    }

    @Operation(summary = "선택 거래처 일괄 적용",
            description = """
                    거래처구분으로 좁혀 고른 **여러 거래처에 같은 값을 한 번에** 반영한다(정본 34p).
                    발주처가 공급률을 거래처구분별 대표값으로 운영하기 때문이다(특약점 일괄 70, B2B 일괄 85).

                    · 기본은 **기존 매핑을 건드리지 않는다**(overwrite=false).
                      예외 단가를 넣어둔 거래처가 조용히 덮이면 잘못된 금액으로 매출이 등록된다.
                    · 건너뛴 거래처는 **코드까지** 응답에 담는다 — 건수만으로는 예외가 지켜진 건지
                      누락인지 구분할 수 없다.""")
    @PutMapping("/bulk")
    public ApiResponse<PartnerSupplyRateBulkResult> bulkApply(
            @Valid @RequestBody PartnerSupplyRateBulkRequest req) {
        return ApiResponse.success(service.bulkApply(req));
    }

    @Operation(summary = "거래처별 공급률 삭제",
            description = "논리삭제(행은 남고 삭제자·시각 기록). 지우면 도서 기본정보의 공급률이 바탕값이 된다.")
    @DeleteMapping("/{partnerId}/{majorCategory}")
    public ApiResponse<Void> delete(@PathVariable Long partnerId,
                                    @PathVariable MajorCategory majorCategory) {
        service.delete(partnerId, majorCategory);
        return ApiResponse.success(null);
    }

    @Operation(summary = "거래처별 단가 엑셀 업로드(33p 탭3)",
            description = """
                    거래처별 공급률·할인액을 일괄 등록·수정한다.
                    키는 **거래처코드 × 대분류** — 도서 단위가 아니다
                    (정본 34p "같은 거래처라도 **대분류별** 공급률이 다르게 설정 가능").

                    컬럼: **거래처코드·대분류**(필수) + 공급률·할인액·Web게시·사용여부(선택).
                    대분류는 코드값(`TEXTBOOK`)이나 한글명(`교재`) 아무 쪽이나 된다.

                    ### 양식 = 목록 다운로드 파일 그대로
                    `/export`로 받은 파일에서 값만 고쳐 다시 올리면 된다. **헤더 이름으로 읽어서**
                    열 순서를 바꾸거나 메모 열을 끼워 넣어도 되고, 모르는 열은 무시한다.
                    **필수 열이 없으면 파일 전체를 거부**한다(양식이 틀린 것이라 행 단위 오류로 흘리면 안 된다).

                    ### 결과
                    행마다 `CREATED / UPDATED / ERROR` + 엑셀 행번호. **한 행이 틀려도 나머지는 들어간다.**
                    ‼️`created`/`updated` 숫자를 볼 것 — 고치려고 올렸는데 전부 신규면 거래처코드가 안 맞은 것이다.""")
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<com.daesung.sales.product.dto.MasterUploadResponse> upload(
            @io.swagger.v3.oas.annotations.Parameter(description = "거래처별단가 xlsx", required = true)
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file) {
        return ApiResponse.success(uploadService.uploadSupplyRates(file));
    }

}
