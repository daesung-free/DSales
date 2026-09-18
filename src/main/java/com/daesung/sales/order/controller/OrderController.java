package com.daesung.sales.order.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.dsre.gateway.ExamRow;
import com.daesung.sales.dsre.gateway.SubjectRow;
import com.daesung.sales.order.dto.OrderCreateRequest;
import com.daesung.sales.order.dto.OrderCreateResponse;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.DsreOrderRow;
import com.daesung.sales.dsre.gateway.LogisMode;
import com.daesung.sales.dsre.gateway.OrderState;
import com.daesung.sales.order.dto.OrderStateChangeRequest;
import com.daesung.sales.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 주문 조회 · 진행상태. 실제 경로: /api/v1/orders.
 *
 * <p><b>상태 원본은 DSRE2 {@code tbl_request_info.STATE}다.</b> 우리는 복사본을 두지 않고 그 값을
 * 직접 읽고 쓴다 — 그래서 동기화 로직이 없고, 우리가 바꾸면 DSRE2 데스크톱에 즉시 보인다.
 *
 * <p>발주처 확정(2026-08-11) "DSRE는 그대로 사용"에 따라 전이는 대부분 DSRE2 데스크톱 소관이다.
 * <b>우리 몫은 물류 담당자가 하는 뒤쪽 세 전이</b>다(자료요청서 3-2(가).진행상태) —
 * 거래명세서 출력(S→W, 자동) · <b>되돌리기(W→S, 수동)</b> · <b>발송완료(W→D, 다건 일괄)</b>.
 * 명세서 출력이 원래부터 매출프로그램 기능이었고(레거시 리포트 21종, DSRE2엔 명세서 화면 없음),
 * 되돌리기·발송처리도 같은 물류 담당자가 같은 화면에서 하는 일이라 함께 둔다.
 * 앞쪽(접수·검수·취소)은 order 사이트와 DSRE2 소관이라 건드리지 않는다.
 *
 * <p>DSRE 연동이 꺼져 있으면(daesung.dsre.enabled=false) 이 화면 자체가 뜨지 않는다.
 */
@Tag(name = "주문 · 진행상태",
        description = "DSRE2 주문·진행상태 조회 + 물류 담당자 몫 전이(명세서 발급·되돌리기·발송완료). "
                + "접수·검수·취소는 order 사이트와 DSRE2 소관.")
@RestController
@RequestMapping("/orders")
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderController {

    private final DsreGateway dsreGateway;
    private final OrderService orderService;
    private final ExcelExportUtil excel;

    @Operation(summary = "주문 삭제(접수완료 건만)",
            description = """
                    잘못 넣은 주문을 지운다. 발주처 확정(2026-08-14 [4]) —
                    "주문(접수완료) — **상품검수로 넘어가기 전까지** 영업·관리자가 삭제 가능".

                    · **접수완료(A)에서만** 됩니다. 상품검수 이후는 물류가 이미 움직인 건이라
                      그때 지우면 작업지시와 실물이 어긋납니다 → 400.
                    · ⚠️**물리삭제가 아닙니다.** DSRE2가 그렇게 정의합니다 —
                      `STATE = 'C' : 삭제`. 행을 지우면 반·과목 수량까지 사라져
                      "무엇이 있었는지"가 남지 않고, order 사이트·DSRE2 데스크톱이
                      같은 행을 보고 있어 우리가 지울 자리가 아닙니다.
                    · 조회와 변경 사이에 DSRE2 쪽이 먼저 옮겼으면 `changed=false`로 돌려줍니다
                      (성공으로 보고하면 담당자가 속습니다).
                    · 누가·언제·왜 지웠는지는 상태변경 이력에 남습니다.""")
    @DeleteMapping("/{reqCd}")
    public ApiResponse<OrderService.ItemResult> delete(
            @Parameter(description = "신청번호(REQ_CD)", required = true) @PathVariable int reqCd,
            @Parameter(description = "삭제 사유") @RequestParam(required = false) String reason) {
        return ApiResponse.success(orderService.delete(reqCd, reason));
    }

    @Operation(summary = "신청 가능 시행 목록",
            description = """
                    주문 등록 1단계 — **무엇을 신청할지** 고른다. 원천은 DSRE2 상품상세(시행)다.

                    · **판매중인 것만** 내려준다(사용·판매 여부 Y + 판매종료일이 오늘 이후).
                      끝난 시행으로는 신청할 수 없다.
                    · `easyYn`이 **간편신청 가능 여부**다. 시행마다 정해져 있으니
                      화면은 이 값을 보고 입력칸(인원 3칸 / 과목별 수량)을 고른다.
                    · `dtlCd`는 물류단가가 키로 쓰는 그 **시행코드**와 같은 값이다.""")
    @GetMapping("/exams")
    public ApiResponse<List<ExamRow>> exams(
            @Parameter(description = "시행명·상품명 부분일치. 미지정=전체")
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(dsreGateway.listExams(keyword));
    }

    @Operation(summary = "시행별 과목 목록",
            description = """
                    주문 등록 2단계(과목신청일 때만) — 그 시행에 **어떤 과목이 있는지**.

                    신청 시 계산처리 대상(`DISP_GN='Y'`)만 내려준다 — 레거시 신청 화면과 같은 조건이다.
                    간편신청(`easyYn='Y'`)으로 넣을 거라면 부를 필요가 없다.""")
    @GetMapping("/exams/{dtlCd}/subjects")
    public ApiResponse<List<SubjectRow>> subjects(
            @Parameter(description = "시행코드(DTL_CD)", required = true) @PathVariable int dtlCd) {
        return ApiResponse.success(dsreGateway.listSubjects(dtlCd));
    }

    @Operation(summary = "신규 주문 등록",
            description = """
                    주문을 새로 만든다. 구조는 **주문 → 반 → 과목수량 3단**이고,
                    레거시 특약점 사이트의 신청과 같은 모양이다.

                    · **테이블 셋을 한 트랜잭션으로** 넣는다. 중간에 실패하면 반·수량이 빠진
                      반쪽 주문이 남아, 물류가 무엇을 보낼지 알 수 없게 된다.
                    · **등록 직후 상태는 항상 `A`(접수완료)**다. 이후 전이(상품검수→발송완료)는
                      기존 상태변경 경로를 탄다.
                    · 반마다 **간편신청(인원 3칸)** 또는 **과목신청(과목별 수량)** 중 하나로 채운다.
                      `applyType`을 안 주면 과목 수량 유무로 판정한다.
                    · ‼️간편인데 인원이 0이거나 과목신청인데 수량이 없으면 **400**이다.
                      조용히 통과시키면 물류가 무엇을 몇 개 보낼지 모르는 주문이 선다.

                    ⚠️이 등록은 **발주처 DB(DSRE2)에 직접 기록**된다.""")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderCreateResponse> create(@Valid @RequestBody OrderCreateRequest req) {
        return ApiResponse.success(orderService.create(req));
    }

    @Operation(summary = "거래명세서 발급 처리 → 발송준비중 전환",
            description = """
                    물류담당자가 거래명세서를 출력 처리한 시점에 호출한다.
                    진행상태를 **상품준비중(S) → 발송준비중(W)** 으로 바꾼다(발주처 확정 3-2(가) 5번).

                    · 상태 원본은 DSRE2라 여기서 바꾸면 DSRE2 데스크톱에도 즉시 반영된다(복사본 없음).
                    · **상품준비중일 때만** 바뀐다. 이미 발송완료 등이면 되돌리지 않고
                      `changed=false`로 응답한다 — 명세서 재출력은 실무에서 흔해 오류로 막지 않는다.
                    · 되돌리기(발송준비중 → 상품준비중)와 발송완료는 `POST /orders/state`로 한다.
                    · DSRE2엔 이력 테이블이 없어, 우리가 바꾼 건은 상태변경 이력에 남긴다.""")
    @PostMapping("/{reqCd}/issue-statement")
    public ApiResponse<OrderService.IssueResult> issueStatement(
            @Parameter(description = "신청번호(REQ_CD)", example = "78331") @PathVariable int reqCd) {
        return ApiResponse.success(orderService.issueStatement(reqCd));
    }

    @Operation(summary = "진행상태 다건 일괄 전환(되돌리기 · 발송완료)",
            description = """
                    체크한 주문들의 진행상태를 한 번에 바꾼다.
                    근거: 자료요청서 3-2(가).진행상태 — 발송준비중 "**되돌릴 때는 수동 전환**",
                    발송완료 "물류가 발송 처리(**체크박스 다건 일괄** 포함)".

                    ### 바꿀 수 있는 전이
                    | 현재 | → | 목표 | 쓰임 |
                    |---|---|---|---|
                    | 상품준비중(S) | → | 발송준비중(W) | 거래명세서 출력(자동 전환의 수동판) |
                    | 발송준비중(W) | → | 상품준비중(S) | **되돌리기(한 칸)** |
                    | 발송준비중(W) | → | 발송완료(D) | **발송 처리** |

                    · 표에 **없는 전이는 거부**한다. 아무 상태로나 뛰게 열면 접수완료가 곧바로
                      발송완료가 되는 주문이 생기고, 검수·준비 단계가 있으나 마나가 된다.
                    · 되돌리기는 **한 칸(W→S)** 뿐이다 — 발송완료를 되돌리는 경로는 정본에 없다.
                    · 접수·검수·취소는 order 사이트와 DSRE2 데스크톱 소관이라 여기서 못 바꾼다.

                    ### 실패 처리
                    · **한 건이 안 된다고 전체를 실패시키지 않는다.** 100건을 체크했는데
                      이미 발송완료된 1건 때문에 전부 다시 고르는 것은 실무에서 못 쓴다.
                      건별로 `changed`와 넘어간 `message`를 준다 — `skipped`가 0이 아니면 확인할 것.
                    · 조회와 변경 사이에 DSRE2 데스크톱이 먼저 옮겼으면 **성공으로 치지 않는다**
                      (조건부 UPDATE). "그 사이 바뀌었다"고 알려준다.

                    ### 이력
                    바꾼 건은 상태변경 이력(`status_history`)에 남는다. DSRE2는 제자리 UPDATE라
                    이전 값이 사라져서, 우리가 안 남기면 **되돌린 사실 자체가 어디에도 없다**.
                    `reason`은 선택이지만 되돌리기는 적어 두는 편이 좋다(감사에서 묻는 것은 "왜"다).""")
    @PostMapping("/state")
    public ApiResponse<OrderService.BulkStateResult> changeState(
            @Valid @RequestBody OrderStateChangeRequest req) {
        return ApiResponse.success(orderService.changeState(req.reqCds(), req.toState(), req.reason()));
    }

    @Operation(summary = "주문·진행상태 조회",
            description = """
                    신청일 기간으로 DSRE2 주문을 조회한다. 진행상태는 DSRE2 원본값을 한글명과 함께 돌려준다.

                    · **기간을 생략하면 올해 1/1~오늘**이다(다른 조회 API와 같은 규칙).
                    · 응답은 다른 목록 API와 같은 **페이지 형태**(`content`/`totalElements`)다.
                      ⚠️단 DSRE2 조회는 저장함수로 인원을 계산하며 한 번에 결과를 만들어
                      SQL 페이징이 없다. 전량을 받아 서버에서 자르므로 **총건수는 항상 전체**다.

                    · 상태값: A 접수완료 / G 상품검수 / S 상품준비중 / W 발송준비중 / D 발송완료 / C 삭제
                    · ⚠️ 취소분은 **본사·물류가 삭제한 건만** C로 조회된다.
                      특약점(지사)이 삭제한 건은 원본 행이 지워져 조회되지 않는다.
                    · 인원은 DSRE2 저장함수(FUNC_REQINWON_GET) 산출값이다.""")
    @GetMapping
    public ApiResponse<com.daesung.sales.common.response.PageResponse<DsreOrderRow>> orders(
            @Parameter(description = "신청일 시작(yyyy-MM-dd). 미지정 시 올해 1월 1일")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "신청일 종료(yyyy-MM-dd). 미지정 시 오늘")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "페이지(0부터)") @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "페이지 크기") @RequestParam(defaultValue = "100") int size,
            @Parameter(description = "진행상태(미지정=전체)") @RequestParam(required = false) OrderState state,
            @Parameter(description = "거래처코드(미지정=전체)") @RequestParam(required = false) String custCode,
            @Parameter(description = "구분 ALL/NORMAL(정상)/ACCIDENT(사고). 미지정=전체")
            @RequestParam(required = false, defaultValue = "ALL") LogisMode mode) {
        // ★날짜를 필수로 두었더니 화면이 못 불렀다(파라미터를 안 실어 400).
        //   다른 조회 API는 이미 기간을 생략하면 올해로 잡는다 — /orders만 예외였다.
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        // ★DSRE2 조회는 저장함수로 인원을 계산하며 한 번에 결과를 만든다.
        //   SQL에 LIMIT을 걸면 페이지마다 인원·합계가 달라지므로, 전량을 받아 여기서 자른다.
        return ApiResponse.success(com.daesung.sales.common.response.PageResponse.ofList(
                dsreGateway.findOrders(from, to, state, custCode, mode), page, size));
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
                new Col("총수량", "totalQty"), new Col("품목건수", "itemCount"),
                new Col("예상금액", "estimatedAmount"),
                new Col("성적처리", "procYn"), new Col("담당선생님", "teacher"),
                new Col("연락처", "tel"), new Col("주소", "address"), new Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("주문진행상태", cols,
                dsreGateway.findOrders(fromDate, toDate, state, custCode, mode));
        return excel.asDownload(xlsx, "주문_진행상태.xlsx");
    }
}
