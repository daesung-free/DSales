package com.daesung.sales.product.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.BomRegisterRequest;
import com.daesung.sales.product.dto.BomResponse;
import com.daesung.sales.product.dto.PartnerPriceBulkRequest;
import com.daesung.sales.product.dto.PartnerPriceBulkResult;
import com.daesung.sales.product.dto.PartnerPriceRequest;
import com.daesung.sales.product.dto.ProductFlagBulkRequest;
import com.daesung.sales.product.dto.ProductFlagBulkResult;
import com.daesung.sales.product.dto.PartnerPriceResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.dto.ProductUpdateRequest;
import com.daesung.sales.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 기초관리 - 상품(도서) 관리. 실제 경로: /api/v1/masters/products. */
@Tag(name = "기초관리 · 상품", description = "도서/상품 마스터 관리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/products")
public class ProductController {

    private final ProductService productService;
    private final com.daesung.sales.common.excel.ExcelExportUtil excel;

    @Operation(summary = "상품 목록 조회", description = "keyword(코드/명 부분일치)로 검색, 페이징·정렬 지원")
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @Parameter(description = "검색어(상품코드 또는 상품명 부분일치)") @RequestParam(required = false) String keyword,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(productService.findAll(keyword, pageReq.toPageable()));
    }

    @Operation(summary = "도서 목록 엑셀 다운로드", description = "검색조건 전체를 xlsx로(도서관리 기본정보).")
    @GetMapping("/export")
    public org.springframework.http.ResponseEntity<byte[]> listExport(@RequestParam(required = false) String keyword) {
        var cols = java.util.List.of(
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("도서코드", "code"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("도서명", "name"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("콘텐츠구분", "contentType"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("분류코드", "catCode"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("분류명", "catName"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("정가", "price"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("면세", "taxFree"),
                // 발주처 회신 2026-08-20: '매출구분' → '세부구분'으로 명칭 변경. 집계 기준인 대분류를 앞에 둔다.
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("대분류", "majorCategoryName"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("세부구분", "salesDivision"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("수불부노출", "ledgerVisible"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("재고관리", "stockManaged"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("사용여부", "useYn"));
        byte[] xlsx = excel.toXlsx("도서목록", cols,
                productService.findAll(keyword, org.springframework.data.domain.PageRequest.of(0, 100000)).getContent());
        return excel.asDownload(xlsx, "도서목록.xlsx");
    }

    @Operation(summary = "상품 상세 조회", description = "id로 단건 조회. 없으면 404")
    @GetMapping("/{id}")
    public ApiResponse<ProductResponse> get(@PathVariable Long id) {
        return ApiResponse.success(productService.findById(id));
    }

    @Operation(summary = "상품 등록", description = "상품코드 중복 시 400 반환")
    @PostMapping
    public ApiResponse<ProductResponse> create(@Valid @RequestBody ProductCreateRequest req) {
        return ApiResponse.success(productService.create(req));
    }

    @Operation(summary = "상품 수정", description = "코드는 불변. 없으면 404")
    @PutMapping("/{id}")
    public ApiResponse<ProductResponse> update(@PathVariable Long id,
                                               @Valid @RequestBody ProductUpdateRequest req) {
        return ApiResponse.success(productService.update(id, req));
    }

    @Operation(summary = "상품 삭제(비활성화)", description = "물리삭제가 아니라 useYn=false 논리삭제")
    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable Long id) {
        productService.deactivate(id);
        return ApiResponse.success();
    }

    @Operation(summary = "BOM 구성 조회", description = "완제품(세트)의 구성품·비율")
    @GetMapping("/{id}/bom")
    public ApiResponse<BomResponse> getBom(@PathVariable Long id) {
        return ApiResponse.success(productService.getBom(id));
    }

    @Operation(summary = "BOM 구성 등록", description = "완제품의 구성품·비율 등록(기존 구성 대체). 완제품은 세트로 표시됨")
    @PutMapping("/{id}/bom")
    public ApiResponse<BomResponse> registerBom(@PathVariable Long id,
                                                @Valid @RequestBody BomRegisterRequest req) {
        return ApiResponse.success(productService.registerBom(id, req));
    }

    // ── 거래처별 단가·노출 매핑(도서관리 3번째 탭) ─────────────────────────

    @Operation(summary = "거래처별 단가·노출 매핑 목록",
            description = "도서의 거래처별 공급률·노출여부 매핑 목록. 단가는 정가×공급률로 파생.")
    @GetMapping("/{id}/partner-prices")
    public ApiResponse<java.util.List<PartnerPriceResponse>> getPartnerPrices(@PathVariable Long id) {
        return ApiResponse.success(productService.getPartnerPrices(id));
    }

    @Operation(summary = "거래처별 단가 매핑 단건 조회(자동조회)",
            description = "도서×거래처 매핑 조회. 매출등록 시 공급률·단가 자동조회용. 없으면 404.")
    @GetMapping("/{id}/partner-prices/{partnerId}")
    public ApiResponse<PartnerPriceResponse> getPartnerPrice(@PathVariable Long id, @PathVariable Long partnerId) {
        return ApiResponse.success(productService.getPartnerPrice(id, partnerId));
    }

    @Operation(summary = "거래처별 단가·노출 매핑 등록/수정",
            description = "도서×거래처 공급률·노출여부 upsert(있으면 수정, 없으면 생성).")
    @PutMapping("/{id}/partner-prices/{partnerId}")
    public ApiResponse<PartnerPriceResponse> upsertPartnerPrice(
            @PathVariable Long id, @PathVariable Long partnerId, @Valid @RequestBody PartnerPriceRequest req) {
        return ApiResponse.success(productService.upsertPartnerPrice(id, partnerId, req));
    }

    @Operation(summary = "도서 Y/N 항목 일괄 변경",
            description = """
                    화면에서 고른 여러 도서의 Y/N 항목을 한 번에 바꾼다
                    (발주처 요청: "각 열을 일괄로 처리할 수 있는 기능(전체선택 등)").

                    · **지정한 항목만** 바뀐다. 안 보낸 항목은 그대로 둔다.
                    · 요청 건수와 **실제로 바뀐 건수를 나눠** 돌려준다 —
                      이미 같은 값이면 안 바뀌므로 "몇 건 적용"만으로는 무슨 일이 났는지 모른다.
                    · 건별로 **변경이력에 남는다**. 잘못 눌렀을 때 되짚을 수 있어야 한다.
                    · ⚠️재고관리를 끄면 그 도서는 매출을 넣어도 재고가 차감되지 않는다.""")
    @PutMapping("/flags")
    public ApiResponse<ProductFlagBulkResult> bulkUpdateFlags(
            @Valid @RequestBody ProductFlagBulkRequest req) {
        return ApiResponse.success(productService.bulkUpdateFlags(req));
    }

    @Operation(summary = "거래처별 단가 일괄 적용",
            description = """
                    여러 거래처에 같은 공급률을 한 번에 적용한다.
                    발주처가 공급률을 거래처구분별 대표값으로 운영해(특약점 일괄 70, B2B 일괄 85)
                    건별 등록으로는 손이 너무 많이 가기 때문이다.

                    · 기본은 **기존 매핑을 건드리지 않는다**(overwrite=false).
                      예외 단가를 넣어둔 거래처가 일괄 적용에 조용히 덮이면 잘못된 금액으로 매출이 등록된다.
                    · 건너뛴 거래처는 코드까지 응답에 담아, 예외가 지켜진 건지 누락인지 구분할 수 있게 한다.""")
    @PutMapping("/{id}/partner-prices")
    public ApiResponse<PartnerPriceBulkResult> bulkUpsertPartnerPrices(
            @PathVariable Long id, @Valid @RequestBody PartnerPriceBulkRequest req) {
        return ApiResponse.success(productService.bulkUpsertPartnerPrices(id, req));
    }

    @Operation(summary = "거래처별 단가 매핑 삭제")
    @DeleteMapping("/{id}/partner-prices/{partnerId}")
    public ApiResponse<Void> deletePartnerPrice(@PathVariable Long id, @PathVariable Long partnerId) {
        productService.deletePartnerPrice(id, partnerId);
        return ApiResponse.success(null);
    }
}
