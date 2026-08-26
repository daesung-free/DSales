package com.daesung.sales.sale.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dsre.gateway.DuffChargeMode;
import com.daesung.sales.sale.dto.DuffImportResponse;
import com.daesung.sales.sale.service.DuffSalesImportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 매출관리 · 매출일괄등록(14p, 더프). DSRE2 의존 → daesung.dsre.enabled=true일 때만.
 *
 * <p>{@code /sales/bulk}(교재)와 <b>다른 화면</b>이다 — 더프는 응시인원 기반 종량제라
 * 부수 기반과 계산이 다르다(정본 14p).
 */
@Tag(name = "매출관리 · 매출일괄등록(더프)",
        description = "DSRE2 신청내역을 응시인원 기반으로 매출 일괄 등록(14p). 소스키 멱등")
@RestController
@RequestMapping("/sales/duff")
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DuffSalesController {

    private final DuffSalesImportService duffSalesImportService;

    private static final String MODE_DESC = """
            처리구분 모드. **정가(단가) 자체가 달라진다** — tbl_product_amt가 처리/비처리별로
            단가를 따로 갖고 있어서, 단순한 조회 필터가 아니다.
            ALL(모두·기본, 신청건의 처리구분을 따름) / PROCESSED(처리) / UNPROCESSED(비처리)""";

    private static final String COMPLETE_DESC =
            "true면 발송완료(state='D') 건만. 레거시 「발송완료만」 체크박스와 같다";

    @Operation(summary = "매출일괄등록(더프) 미리보기(dryRun)",
            description = """
                    DSRE2 신청내역을 조회만 — 매출 생성 없음. 정본 14p 항목(처리순번·신청일자·매출코드·
                    거래처·학교·학년·처리구분·청구구분·신청/처리/비처리 인원·단가·총금액)을 그대로 낸다.
                    청구인원은 우선순위 **등록(청구구분 R) → 조회모드 → 처리구분 N이면 비처리 → 처리**로 정해지며,
                    어느 근거로 골랐는지 chargeBasis에 실린다.""")
    @GetMapping("/preview")
    public ApiResponse<DuffImportResponse> preview(
            @Parameter(description = "신청일 시작(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "신청일 종료(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = MODE_DESC) @RequestParam(required = false) DuffChargeMode mode,
            @Parameter(description = COMPLETE_DESC)
            @RequestParam(required = false, defaultValue = "false") boolean onlyComplete) {
        return ApiResponse.success(
                duffSalesImportService.importSales(fromDate, toDate, mode, onlyComplete, true));
    }

    @Operation(summary = "매출일괄등록(더프) 실행",
            description = """
                    신청내역을 매출로 생성. 소스키(모드:신청:거래처:학교:과목) 멱등이라
                    재실행해도 이미 등록분은 스킵된다.
                    인원 0 이하 건은 등록하지 않는다(정본 14p). 마감된 달로는 등록되지 않는다.""")
    @PostMapping("/import")
    public ApiResponse<DuffImportResponse> importSales(
            @Parameter(description = "신청일 시작(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "신청일 종료(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = MODE_DESC) @RequestParam(required = false) DuffChargeMode mode,
            @Parameter(description = COMPLETE_DESC)
            @RequestParam(required = false, defaultValue = "false") boolean onlyComplete) {
        return ApiResponse.success(
                duffSalesImportService.importSales(fromDate, toDate, mode, onlyComplete, false));
    }
}
