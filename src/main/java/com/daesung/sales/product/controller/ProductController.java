package com.daesung.sales.product.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.BomRegisterRequest;
import com.daesung.sales.product.dto.BomResponse;
import com.daesung.sales.product.dto.ProductFlagBulkRequest;
import com.daesung.sales.product.dto.ProductFlagBulkResult;
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

    private final com.daesung.sales.product.service.ProductUploadService uploadService;

    private final ProductService productService;
    private final com.daesung.sales.common.excel.ExcelExportUtil excel;

    @Operation(summary = "상품 목록 조회", description = "keyword(코드/명 부분일치)로 검색, 페이징·정렬 지원")
    @GetMapping
    public ApiResponse<PageResponse<ProductResponse>> list(
            @Parameter(description = "검색어(상품코드 또는 상품명 부분일치)") @RequestParam(required = false) String keyword,
            @Parameter(description = """
                    수불부노출 필터 — 제품수불부(11p) 집계 대상만/제외만 보기.
                    ★단가노출과 **별개 축**이다(발주처 2026-08-21 E-7로 분리).""")
            @RequestParam(required = false) Boolean ledgerVisible,
            @Parameter(description = """
                    단가노출 필터 — 거래처별 단가를 매기는 도서만/제외만 보기.
                    수불부엔 안 나와도 단가는 매기는 상품이 있어 수불부노출과 따로 둔다.""")
            @RequestParam(required = false) Boolean priceVisible,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(productService.findAll(keyword, ledgerVisible, priceVisible, pageReq.toPageable()));
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
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("단가노출", "priceVisible"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("재고관리", "stockManaged"),
                new com.daesung.sales.common.excel.ExcelExportUtil.Col("사용여부", "useYn"));
        byte[] xlsx = excel.toXlsx("도서목록", cols,
                productService.findAll(keyword, null, null,
                        org.springframework.data.domain.PageRequest.of(0, 100000)).getContent());
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

    // ── 거래처별 단가(34p)는 별도 컨트롤러다 ──────────────────────────────
    //   화면은 도서관리 3번째 탭이지만 데이터에 도서 차원이 없다 —
    //   정본 34p가 "거래처별로 상품군(대분류)마다"라고 못박고 있다.
    //   경로: /masters/partner-supply-rates (PartnerSupplyRateController).

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


    private static final String UPLOAD_COMMON = """

            ### 양식 = **목록 다운로드 파일 그대로**
            별도 양식을 만들지 않았다. `/export`로 받은 파일에서 **값만 고쳐 다시 올리면 된다**.
            · **헤더 이름으로 읽는다** — 열 순서를 바꾸거나 메모 열을 끼워 넣어도 된다.
              위치로 읽으면 열 하나만 밀려도 정가 자리에 수량이 조용히 들어간다.
            · 모르는 열은 무시한다. **필수 열이 없으면 파일 전체를 거부**한다 —
              양식이 틀린 파일을 행 단위 오류로 흘리면 수백 줄의 같은 오류를 보고서야 알게 된다.
            · Y/N 칸은 `Y·예·O·1` / `N·아니오·X·0`을 받는다. 알 수 없는 값은 **오류**다
              ("Yes"를 조용히 false로 읽으면 끈 적 없는 항목이 꺼진다).

            ### 결과
            행마다 `CREATED / UPDATED / ERROR` + 엑셀 행번호를 돌려준다.
            **한 행이 틀려도 나머지는 들어간다** — 수백 줄에서 한 줄 오타로 전부 되돌리면
            담당자는 고칠 곳을 못 찾은 채 처음부터 다시 해야 한다.
            ‼️`created`/`updated` 숫자를 볼 것. 고치려고 올렸는데 전부 신규면 **코드가 안 맞은 것**이다.""";

    @Operation(summary = "도서 기본정보 엑셀 업로드(33p 탭1)",
            description = "도서 마스터를 일괄 등록·수정한다. **도서코드가 키** — 있으면 고치고 없으면 만든다.\n\n"
                    + "★**빈 칸은 '지우기'가 아니라 '그대로'다.** 담당자는 고칠 열만 채워 올린다. "
                    + "빈 칸을 지움으로 처리하면 정가만 고치려던 사람이 분류·공급률을 통째로 날린다."
                    + UPLOAD_COMMON)
    @PostMapping(value = "/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<com.daesung.sales.product.dto.MasterUploadResponse> uploadProducts(
            @io.swagger.v3.oas.annotations.Parameter(description = "도서목록 xlsx", required = true)
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file) {
        return ApiResponse.success(uploadService.uploadProducts(file));
    }

    @Operation(summary = "세트구성(BOM) 엑셀 업로드(33p 탭2)",
            description = "세트별 구성품을 일괄 등록한다. 컬럼: **세트도서코드·구성도서코드·소요수량** "
                    + "(선택: 구성회차·시행예정일·분리포장·자재구분).\n\n"
                    + "★한 세트의 구성품이 **여러 행**으로 오고, 등록은 **세트 단위 전량 교체**다. "
                    + "그래서 **한 세트의 구성품 중 하나라도 틀리면 그 세트는 통째로 건너뛴다** — "
                    + "일부만 넣으면 구성품이 빠진 반쪽 BOM이 만들어지고, 그 세트로 조립하면 자재가 안 빠진다. "
                    + "다른 세트는 영향받지 않는다."
                    + UPLOAD_COMMON)
    @PostMapping(value = "/bom/upload", consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<com.daesung.sales.product.dto.MasterUploadResponse> uploadBom(
            @io.swagger.v3.oas.annotations.Parameter(description = "세트구성 xlsx", required = true)
            @org.springframework.web.bind.annotation.RequestPart("file")
            org.springframework.web.multipart.MultipartFile file) {
        return ApiResponse.success(uploadService.uploadBom(file));
    }

}
