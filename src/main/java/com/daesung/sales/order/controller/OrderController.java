package com.daesung.sales.order.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.DsreOrderRow;
import com.daesung.sales.dsre.gateway.LogisMode;
import com.daesung.sales.dsre.gateway.OrderState;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 조회 · 진행상태 (읽기 전용). 실제 경로: /api/v1/orders.
 *
 * <p><b>상태는 DSRE2가 소유한다.</b> 발주처 확정(2026-08-11) "DSRE는 그대로 사용" 에 따라
 * 상태 전이(접수완료→상품검수→…→발송완료)는 DSRE2 데스크톱이 계속 수행하고,
 * 우리 매출프로그램은 조회만 한다. 그래서 이 컨트롤러에는 상태 변경 엔드포인트가 없다.
 *
 * <p>DSRE 연동이 꺼져 있으면(daesung.dsre.enabled=false) 이 화면 자체가 뜨지 않는다.
 */
@Tag(name = "주문 · 진행상태",
        description = "DSRE2 주문·진행상태 조회(읽기 전용). 상태 변경은 DSRE2 소관이라 제공하지 않는다.")
@RestController
@RequestMapping("/orders")
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderController {

    private final DsreGateway dsreGateway;
    private final ExcelExportUtil excel;

    @Operation(summary = "주문·진행상태 조회",
            description = """
                    신청일 기간으로 DSRE2 주문을 조회한다. 진행상태는 DSRE2 원본값을 한글명과 함께 돌려준다.

                    · 상태값: A 접수완료 / G 상품검수 / S 상품준비중 / W 발송준비중 / D 발송완료 / C 삭제
                    · ⚠️ 취소분은 **본사·물류가 삭제한 건만** C로 조회된다.
                      특약점(지사)이 삭제한 건은 원본 행이 지워져 조회되지 않는다.
                    · 인원은 DSRE2 저장함수(FUNC_REQINWON_GET) 산출값이다.""")
    @GetMapping
    public ApiResponse<List<DsreOrderRow>> orders(
            @Parameter(description = "신청일 시작(yyyy-MM-dd)", required = true)
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "신청일 종료(yyyy-MM-dd)", required = true)
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "진행상태(미지정=전체)") @RequestParam(required = false) OrderState state,
            @Parameter(description = "거래처코드(미지정=전체)") @RequestParam(required = false) String custCode,
            @Parameter(description = "구분 ALL/NORMAL(정상)/ACCIDENT(사고). 미지정=전체")
            @RequestParam(required = false, defaultValue = "ALL") LogisMode mode) {
        return ApiResponse.success(dsreGateway.findOrders(fromDate, toDate, state, custCode, mode));
    }

    @Operation(summary = "주문·진행상태 엑셀 다운로드")
    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) OrderState state,
            @RequestParam(required = false) String custCode,
            @RequestParam(required = false, defaultValue = "ALL") LogisMode mode) {
        List<Col> cols = List.of(
                new Col("신청번호", "reqCd"), new Col("신청일", "reqDate"),
                new Col("진행상태", "stateName"),
                new Col("거래처코드", "custCode"), new Col("거래처명", "custName"),
                new Col("도시", "cityName"),
                new Col("학교/학원코드", "mgrCode"), new Col("학교/학원명", "mgrName"),
                new Col("상품명", "productName"), new Col("시행명", "detailName"),
                new Col("학년", "grade"), new Col("인원", "inwon"), new Col("반수", "classCount"),
                new Col("성적처리", "procYn"), new Col("담당선생님", "teacher"),
                new Col("연락처", "tel"), new Col("주소", "address"), new Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("주문진행상태", cols,
                dsreGateway.findOrders(fromDate, toDate, state, custCode, mode));
        return excel.asDownload(xlsx, "주문_진행상태.xlsx");
    }
}
