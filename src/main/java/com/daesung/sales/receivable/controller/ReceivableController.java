package com.daesung.sales.receivable.controller;

import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.receivable.dto.ArLedgerResponse;
import com.daesung.sales.receivable.dto.ArStatusResponse;
import com.daesung.sales.receivable.dto.CarryforwardResult;
import com.daesung.sales.receivable.dto.CollectionRequest;
import com.daesung.sales.receivable.dto.CollectionLedgerResponse;
import com.daesung.sales.receivable.dto.CollectionResponse;
import com.daesung.sales.receivable.dto.DuffLedgerResponse;
import com.daesung.sales.receivable.entity.CollectionType;
import com.daesung.sales.receivable.service.ReceivableService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 마감관리 · 채권/수금. 실제 경로: /api/v1/closing. 스펙 §마감관리(collections/ar-status) 대응. */
@Tag(name = "마감관리 · 채권/수금", description = "수금 등록/조회 · 이월 생성(idempotent) · 미수금 현황")
@RestController
@RequiredArgsConstructor
@RequestMapping("/closing")
public class ReceivableController {

    private final ReceivableService receivableService;
    private final ExcelExportUtil excel;

    @Operation(summary = "수금 등록",
            description = """
                    수금번호(C) 채번. 채권 잔액에서 차감된다.

                    · **수금구분**(명목) 미입력 시 `도서대금` — 레거시가 화면에서 고정하던 값이다.
                    · **입금구분**=어음일 때만 어음정보(번호/만기/은행/지점)를 저장한다.""")
    @PostMapping("/collections")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CollectionResponse> registerCollection(@Valid @RequestBody CollectionRequest req) {
        return ApiResponse.success(receivableService.registerCollection(req));
    }

    @Operation(summary = "수금 목록 조회",
            description = """
                    기간·거래처 + **구분 2축**으로 수금 내역을 조회한다(정본 23p 데이터 항목).

                    · **수금구분**(`collKind`) = 무슨 명목으로 받은 돈인가. 기본 `도서대금`.
                    · **입금구분**(`collType`) = 어떤 형태로 들어왔는가(현금/어음/선수금/대체).

                    두 축을 하나로 합치면 "**어음으로 받은 도서대금**"을 표현할 수 없다.""")
    @GetMapping("/collections")
    public ApiResponse<PageResponse<CollectionResponse>> listCollections(
            @Parameter(description = "시작일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "수금구분(명목). 미지정=전체", example = "도서대금")
            @RequestParam(required = false) String collKind,
            @Parameter(description = "입금구분(형태) CASH/PROMISSORY/PREPAY/REPLACE. 미지정=전체")
            @RequestParam(required = false) CollectionType collType,
            @ParameterObject PageRequestDto pageReq) {
        return ApiResponse.success(receivableService.searchCollections(
                fromDate, toDate, partnerId, collKind, collType, pageReq.toPageable()));
    }

    @Operation(summary = "수금 수정",
            description = """
                    수금 내용을 고친다(23p "CRUD 전체 가능 화면").
                    **수금번호·거래처는 바꿀 수 없다** — 거래처를 옮기면 두 거래처의 채권 잔액이
                    동시에 틀어진다. 그건 수정이 아니라 취소 후 재등록이다.

                    · **원래 달과 옮겨 갈 달 양쪽**의 월마감을 본다. 옮겨 갈 달만 검사하면
                      마감된 달에서 열린 달로 금액을 빼내는 길이 열린다.
                    · 입금구분을 어음이 아닌 값으로 바꾸면 어음 정보를 지운다 —
                      남겨두면 "현금인데 어음번호가 붙은" 유령 어음이 생긴다.""")
    @PutMapping("/collections/{id}")
    public ApiResponse<CollectionResponse> updateCollection(@PathVariable Long id,
                                                            @Valid @RequestBody CollectionRequest req) {
        return ApiResponse.success(receivableService.updateCollection(id, req));
    }

    @Operation(summary = "수금 삭제(논리삭제)",
            description = """
                    수금을 지운다. **행은 남고 삭제자·시각이 기록된다.**

                    수금은 돈이 들어온 기록이라 지우면 그만큼 **채권 잔액이 늘어난다**.
                    누가 언제 지웠는지 남지 않으면 잔액이 왜 달라졌는지 설명할 수 없다.
                    삭제분은 미수금현황·외상매출장·이월 스냅샷 계산에서 모두 빠진다.

                    ⚠️레거시는 이 기능이 주석 처리돼 막혀 있었으나 정본 23p가 "CRUD 전체"를 요구한다.
                    마감된 달의 수금은 지울 수 없다(PERIOD_LOCKED).""")
    @DeleteMapping("/collections/{id}")
    public ApiResponse<Void> deleteCollection(@PathVariable Long id) {
        receivableService.deleteCollection(id);
        return ApiResponse.success(null);
    }

    @Operation(summary = "수금 조회(소계 포함) 23p",
            description = """
                    수금관리 화면의 본 조회. **조회대상기준 3종**과 **소계**가 붙는다.

                    · `WRITE_DATE`(기장일자) — **기본값**. 기장일자로 걸러 날짜순.
                      ⚠️기장일자가 비어 있는 건(아직 기표 전)은 이 기준에서 빠진다.
                    · `COLLECT_DATE`(수금일자) — 수금일자로 걸러 날짜순.
                    · `PARTNER`(거래처) — 날짜는 수금일자로 걸러 **거래처순**으로 묶는다.

                    소계는 레거시 실물 그대로다 — 날짜 기준은 **일 계·월 계·누 계** 3단,
                    거래처 기준은 **소 계(거래처코드 첫 글자 그룹)·누 계**.
                    정본은 '일계'만 적었지만 레거시는 더 촘촘하다.

                    **페이징하지 않는다** — 소계는 앞뒤 행이 다 있어야 성립해서,
                    페이지를 자르면 잘린 지점의 소계가 틀린 값이 된다.
                    등록·수정용 그리드는 `GET /closing/collections`(페이징)를 쓴다.""")
    @GetMapping("/collections/ledger")
    public ApiResponse<CollectionLedgerResponse> collectionLedger(
            @Parameter(description = "조회대상기준(미지정 시 기장일자)")
            @RequestParam(required = false) CollectionLedgerResponse.Basis basis,
            @Parameter(description = "시작일", required = true) @RequestParam(name = "fromDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일", required = true) @RequestParam(name = "toDate")
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long partnerId,
            @Parameter(description = "수금구분(명목)") @RequestParam(required = false) String collKind,
            @Parameter(description = "입금구분(형태)") @RequestParam(required = false) CollectionType collType) {
        return ApiResponse.success(receivableService.collectionLedger(
                basis, fromDate, toDate, partnerId, collKind, collType));
    }

    @Operation(summary = "수금 조회 엑셀 다운로드(소계 포함)")
    @GetMapping("/collections/ledger/export")
    public ResponseEntity<byte[]> collectionLedgerExport(
            @RequestParam(required = false) CollectionLedgerResponse.Basis basis,
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) String collKind,
            @RequestParam(required = false) CollectionType collType) {
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("소계명", "label"),
                new Col("수금일자", "collDate"), new Col("기장일자", "writeDate"),
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"),
                new Col("수금구분", "collKind"), new Col("입금구분", "collTypeName"),
                new Col("금액", "collAmt"), new Col("어음번호", "promissoryNo"),
                new Col("만기일자", "promissoryDue"), new Col("은행명", "bankName"),
                new Col("지점명", "branchName"), new Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("수금관리", cols, receivableService.collectionLedger(
                basis, fromDate, toDate, partnerId, collKind, collType).rows());
        return excel.asDownload(xlsx, "수금관리_" + fromDate + "_" + toDate + ".xlsx");
    }

    @Operation(summary = "채권 이월 스냅샷 생성(idempotent)",
            description = "해당 연도 이월(=전년말 채권 잔액)을 계산해 저장. 기존 연도분 삭제 후 재생성. "
                    + "레거시의 '조회 시 자동생성' 부수효과를 제거한 명시 API.")
    @PostMapping("/carryforward")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CarryforwardResult> generateCarryforward(
            @Parameter(description = "이월 귀속 연도", example = "2026") @RequestParam int fiscalYear) {
        return ApiResponse.success(receivableService.generateCarryforward(fiscalYear));
    }

    @Operation(summary = "미수금(외상매출) 현황 조회",
            description = "거래처별 잔액 = 이월 + 기간 채권발생(매출+세액−반품) − 수금. 담보비율/경고등급 포함. "
                    + "기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/ar-status")
    public ApiResponse<ArStatusResponse> arStatus(
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "거래처 id 필터") @RequestParam(required = false) Long partnerId) {
        return ApiResponse.success(receivableService.arStatus(fromDate, toDate, partnerId));
    }

    @Operation(summary = "미수금현황 엑셀 다운로드", description = "거래처별 이월·매출·반품·채권발생·수금·잔액·담보비율.")
    @GetMapping("/ar-status/export")
    public ResponseEntity<byte[]> arStatusExport(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) Long partnerId) {
        List<Col> cols = List.of(
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"), new Col("이월", "opening"),
                new Col("매출액", "saleAmount"), new Col("반품액", "returnAmount"), new Col("세액", "tax"),
                new Col("채권발생", "receivableGen"), new Col("수금", "collected"), new Col("잔액", "balance"),
                new Col("담보금액", "assureAmount"), new Col("담보비율", "assureRatio"), new Col("담보등급", "assureLevel"));
        byte[] xlsx = excel.toXlsx("미수금현황", cols, receivableService.arStatus(fromDate, toDate, partnerId).rows());
        return excel.asDownload(xlsx, "미수금현황.xlsx");
    }

    @Operation(summary = "외상매출장 조회(거래처 상세)",
            description = "단일 거래처의 기초이월 + 기간 내 매출/반품/수금 명세 + 일자별 누계(러닝밸런스). "
                    + "기간 미지정 시 올해 1/1~오늘.")
    @GetMapping("/ar-ledger")
    public ApiResponse<ArLedgerResponse> arLedger(
            @Parameter(description = "거래처 id", example = "1") @RequestParam Long partnerId,
            @Parameter(description = "시작일(yyyy-MM-dd, 미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd, 미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(receivableService.arLedger(partnerId, fromDate, toDate));
    }

    @Operation(summary = "외상매출장 '더프모만'(24p)",
            description = """
                    모의고사 매출을 **학교(원)·시행월·학년·처리** 단위로 본다.
                    정본 24p가 "'더프모만' 체크 시 그리드 컬럼 구조 자체가 완전 전환"이라 해서
                    기본 외상매출장과 **다른 응답**으로 낸다 —
                    한 응답에 두 스키마를 우겨넣으면 절반이 늘 비어 있는 DTO가 된다.

                    ```
                    [기본]   일자 · 구분 · 전표번호 · 적요 · 금액 · 잔액   ← GET /closing/ar-ledger
                    [더프모] 일자 · 학교(원)명 · 시행월 · 학년 · 처리 · 정가 · 공급률 · 수량 · 금액
                    ```
                    기본 장부는 **채권 러닝밸런스**를 보는 화면이고 이쪽은 모의고사 매출 세부라 잔액이 없다.

                    · 소계는 레거시 그대로 **학교(원) 계 → 월 계** 순으로 붙는다.
                    · 모의고사 판별은 **대분류**로 한다. 레거시는 `catCode LIKE 'M%A%'`처럼
                      코드 패턴으로 걸렀는데, 그러면 코드체계가 바뀔 때마다 깨진다.
                    · ⚠️**시행월**은 그 상품·회차의 BOM 시행예정일에서 온다.
                      레거시는 도서명을 잘라 만들었고 이름이 규칙에 안 맞으면 `##ERROR`가 찍혔다.
                      우리는 파싱하지 않으므로 BOM에 시행예정일이 없으면 **비운다**.""")
    @GetMapping("/ar-ledger/duff")
    public ApiResponse<DuffLedgerResponse> duffLedger(
            @Parameter(description = "거래처 id", required = true) @RequestParam Long partnerId,
            @Parameter(description = "시작일(미지정 시 올해 1/1)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(미지정 시 오늘)") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return ApiResponse.success(receivableService.duffLedger(partnerId, fromDate, toDate));
    }

    @Operation(summary = "외상매출장 '더프모만' 엑셀 다운로드")
    @GetMapping("/ar-ledger/duff/export")
    public ResponseEntity<byte[]> duffLedgerExport(
            @RequestParam Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<Col> cols = List.of(
                new Col("구분", "rowType"), new Col("소계명", "label"),
                new Col("일자", "date"), new Col("학교(원)명", "schoolName"),
                new Col("시행월", "examMonth"), new Col("학년", "grade"),
                new Col("처리", "procType"), new Col("정가", "unitPrice"),
                new Col("공급률", "supplyRate"), new Col("수량", "qty"), new Col("금액", "amount"));
        byte[] xlsx = excel.toXlsx("외상매출장_더프모", cols,
                receivableService.duffLedger(partnerId, fromDate, toDate).rows());
        return excel.asDownload(xlsx, "외상매출장_더프모.xlsx");
    }

    @Operation(summary = "외상매출장 엑셀 다운로드", description = "단일 거래처 일자별 매출/반품/수금 명세 + 러닝밸런스.")
    @GetMapping("/ar-ledger/export")
    public ResponseEntity<byte[]> arLedgerExport(
            @RequestParam Long partnerId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        List<Col> cols = List.of(
                new Col("일자", "date"), new Col("구분", "kind"), new Col("전표번호", "refNo"),
                new Col("적요", "description"), new Col("금액", "amount"), new Col("잔액", "balance"));
        byte[] xlsx = excel.toXlsx("외상매출장", cols, receivableService.arLedger(partnerId, fromDate, toDate).lines());
        return excel.asDownload(xlsx, "외상매출장.xlsx");
    }
}
