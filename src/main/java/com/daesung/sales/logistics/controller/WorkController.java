package com.daesung.sales.logistics.controller;

import com.daesung.sales.common.excel.ExcelExportUtil;
import com.daesung.sales.common.excel.ExcelExportUtil.Col;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.logistics.dto.ShippingUpdateRequest;
import com.daesung.sales.logistics.dto.TrackingUploadResponse;
import com.daesung.sales.logistics.entity.DeliveryType;
import com.daesung.sales.logistics.dto.WorkOrderResponse;
import com.daesung.sales.logistics.dto.WorkResultRow;
import com.daesung.sales.logistics.service.ShipmentService;
import com.daesung.sales.logistics.service.WorkService;
import com.daesung.sales.warehouse.entity.WarehouseType;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 물류/작업 — 작업요청서 · 작업결과. 실제 경로: /api/v1/logistics.
 *
 * <p>발송 건은 매출등록 시 자동으로 만들어진다(레거시와 동일). 물류는 여기서
 * <b>작업지시를 출력하고, 박스 수·발송일을 채운다</b>. 작업결과는 그 진행을 모아 보는 조회 화면이다.
 */
@Tag(name = "물류 · 작업요청서/작업결과",
        description = "발송 건별 작업지시(무엇을 몇 개)와 진행 현황(출력·박스·발송일)")
@RestController
@RequestMapping("/logistics")
@RequiredArgsConstructor
public class WorkController {

    private final WorkService workService;
    private final ShipmentService shipmentService;
    private final ExcelExportUtil excel;

    @Operation(summary = "작업요청서 조회",
            description = """
                    발송 건과 **그 안에 담을 도서 목록**을 함께 돌려준다(무엇을 몇 개 넣어라).

                    · 발송 건은 **(거래일자 · 거래처 · 학교 · 분류)** 단위다.
                      같은 날 같은 학교로 여러 품목을 등록해도 발송은 한 건이다 —
                      물류는 품목이 아니라 상자 단위로 일한다.
                    · `printed=false`로 **아직 지시가 안 나간 건만** 볼 수 있다(레거시 동일 필터).
                    · 반품은 대상이 아니다(들어오는 물건이라 내보낼 작업이 없다).""")
    @GetMapping("/work-orders")
    public ApiResponse<List<WorkOrderResponse>> workOrders(
            @Parameter(description = "거래일자 시작", required = true)
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "거래일자 종료", required = true)
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "분류(매출구분). 미지정=전체") @RequestParam(required = false) String tradeClass,
            @Parameter(description = "거래처 id. 미지정=전체") @RequestParam(required = false) Long partnerId,
            @Parameter(description = "출력여부 true=출력분/false=미출력분/미지정=전체")
            @RequestParam(required = false) Boolean printed,
            @Parameter(description = "발송구분 COURIER(택배)/FREIGHT(화물). 미지정=전체(미선택 건 포함)")
            @RequestParam(required = false) DeliveryType deliveryType) {
        return ApiResponse.success(
                workService.workOrders(fromDate, toDate, tradeClass, partnerId, printed, deliveryType));
    }

    @Operation(summary = "작업요청서 출력 처리",
            description = """
                    작업지시를 출력한 것으로 표시한다 → 작업결과의 '출력' ○.

                    **재출력해도 최초 시각을 덮지 않는다** — 이 기록의 의미는
                    "언제 처음 작업지시가 나갔나"다. 이미 출력된 건이면 `false`를 돌려준다.""")
    @PostMapping("/work-orders/{id}/print")
    public ApiResponse<Boolean> markPrinted(@PathVariable Long id) {
        return ApiResponse.success(shipmentService.markPrinted(id));
    }

    @Operation(summary = "발송정보 입력",
            description = """
                    박스 수·발송일·발송메모(레거시 작업요청서.vb:915와 같은 항목)에
                    **발송구분·수령인·송장**을 함께 기록한다(26p 확정 항목).

                    · **발송구분(택배/화물)**: 정본 26p 「작업 대기 리스트의 '상태변경' 옆에
                      '발송구분(택배/화물)' 필드 추가」. **기본값은 두지 않는다** —
                      "물류가 매번 수동 판단하는지 자동 기본값이 있는지"가 정본에서 미해결이라,
                      고르지 않은 건을 임의로 한쪽에 넣으면 발송된 것처럼 보인다.
                    · **수령인**: 정본 26p 「'택배' 선택 시 담당자 정보가 노출」.
                    · **송장번호·택배사**: 값 기록만 한다. 택배사 API는 부르지 않는다
                      (연동 여부는 물류팀 인터뷰 회신 대기).

                    **지정하지 않은 항목은 건드리지 않는다** — 박스 수만 고치려다
                    발송일이나 수령인이 지워지면 택배가 누구에게 가는지 알 수 없게 된다.""")
    @PutMapping("/work-orders/{id}/shipping")
    public ApiResponse<Void> updateShipping(@PathVariable Long id,
                                            @Valid @RequestBody ShippingUpdateRequest req) {
        shipmentService.updateShipping(id, req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "송장 등록용 양식 다운로드",
            description = """
                    택배사에 넘길 발송 목록을 xlsx로 내려준다. 택배사가 **송장번호 열만 채워
                    돌려주면** 그대로 업로드(`POST /work-orders/tracking/upload`)하면 된다.

                    맨 앞 **발송건ID**가 대조 키다 — 거래처·학교 이름으로 맞추면
                    같은 날 같은 학교로 두 건이 나갈 때 어느 쪽인지 가릴 수 없다.
                    담당자가 ID를 따로 채울 필요가 없도록 양식에 넣어 둔다.""")
    @GetMapping("/work-orders/tracking/template")
    public ResponseEntity<byte[]> trackingTemplate(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "발송구분. 보통 택배(COURIER)만 뽑는다")
            @RequestParam(required = false) DeliveryType deliveryType,
            @RequestParam(required = false) Long partnerId) {
        List<Col> cols = List.of(
                new Col("발송건ID", "id"), new Col("택배사", "courierName"), new Col("송장번호", "trackingNo"),
                new Col("거래일자", "tradeDate"), new Col("거래처", "partnerName"),
                new Col("학교/학원", "schoolName"), new Col("수령인", "receiverName"),
                new Col("연락처", "receiverPhone"), new Col("Box", "boxCount"),
                new Col("발송구분", "deliveryTypeName"));
        byte[] xlsx = excel.toXlsx("송장등록", cols,
                workService.workOrders(fromDate, toDate, null, partnerId, null, deliveryType));
        return excel.asDownload(xlsx, "송장등록양식_" + fromDate + "_" + toDate + ".xlsx");
    }

    @Operation(summary = "송장번호 엑셀 일괄 등록",
            description = """
                    택배사에서 송장번호가 채워져 돌아온 파일을 그대로 올린다.
                    양식 3컬럼(그 뒤 열은 무시): **발송건ID · 택배사 · 송장번호**.

                    · **한 행이 실패해도 나머지는 반영한다.** 택배사 파일에 우리가 모르는 행이
                      섞였다고 전체가 취소되면, 담당자는 어느 줄이 문제인지 모른 채 처음부터 다시 해야 한다.
                    · 행별 결과(UPDATED/ERROR·사유)를 돌려준다.
                    · ⚠️택배사 API를 부르지 않는다 — 연동 여부는 물류팀 인터뷰 회신 대기 항목이다.""")
    @PostMapping(value = "/work-orders/tracking/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<TrackingUploadResponse> uploadTracking(
            @Parameter(description = "송장번호가 채워진 xlsx", required = true)
            @RequestPart("file") MultipartFile file) {
        return ApiResponse.success(shipmentService.uploadTracking(file));
    }

    @Operation(summary = "작업결과 조회",
            description = """
                    발송 건별 진행 현황. 상품군별 수량(교재·IC 등)·박스 수·발송일을 함께 본다.

                    · '출력'·'완료'는 **날짜가 채워졌는지**로 판단한다(레거시에 상태 컬럼이 없다).
                    · **출고창고**: 물류는 본사물류창고만 보이고, 관리자만 전체·본사물류·위탁을 고를 수 있다
                      (발주처 확정 3-2 나). 물류가 다른 값을 보내도 본사물류창고로 강제된다.
                    · ⚠️'완료'는 레거시에도 값을 넣는 코드가 없어 **항상 false**다.
                      임의로 만들면 화면 의미가 달라져, 발주처 확인 후 붙일 항목으로 남겨 두었다.""")
    @GetMapping("/work-results")
    public ApiResponse<List<WorkResultRow>> workResults(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String tradeClass,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Boolean printed,
            @Parameter(description = "출고창고 구분 MAIN(본사물류창고)/CONSIGN(위탁창고). 미지정=전체(관리자만)")
            @RequestParam(required = false) WarehouseType warehouseType) {
        return ApiResponse.success(
                workService.workResults(fromDate, toDate, tradeClass, partnerId, printed, warehouseType));
    }

    @Operation(summary = "작업결과 엑셀 다운로드")
    @GetMapping("/work-results/export")
    public ResponseEntity<byte[]> workResultsExport(
            @RequestParam(name = "fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(name = "toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(required = false) String tradeClass,
            @RequestParam(required = false) Long partnerId,
            @RequestParam(required = false) Boolean printed,
            @RequestParam(required = false) WarehouseType warehouseType) {
        List<Col> cols = List.of(
                new Col("분류", "tradeClass"), new Col("거래일자", "tradeDate"),
                new Col("거래순번", "tradeSeq"),
                new Col("거래처코드", "partnerCode"), new Col("거래처명", "partnerName"),
                new Col("학교코드", "schoolCode"), new Col("학교명", "schoolName"),
                new Col("출력", "printed"), new Col("완료", "completed"),
                new Col("발송일", "sentDate"), new Col("출고창고", "warehouseName"),
                new Col("수량", "totalQty"),
                new Col("Box", "boxCount"), new Col("발송메모", "sendMemo"), new Col("비고", "memo"));
        byte[] xlsx = excel.toXlsx("작업결과", cols,
                workService.workResults(fromDate, toDate, tradeClass, partnerId, printed, warehouseType));
        return excel.asDownload(xlsx, "작업결과.xlsx");
    }
}
