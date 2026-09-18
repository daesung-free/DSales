package com.daesung.sales.logistics.service;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.logistics.dto.ShippingUpdateRequest;
import com.daesung.sales.logistics.dto.TrackingUploadResponse;
import com.daesung.sales.logistics.entity.Shipment;
import com.daesung.sales.logistics.repository.ShipmentRepository;
import com.daesung.sales.sale.entity.Sale;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.web.multipart.MultipartFile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 발송(작업) 단위 관리. 레거시 {@code sendData} 흐름을 그대로 옮긴다 —
 * 매출등록이 만들고, 작업요청서가 채우고, 작업결과가 읽는다.
 */
@Service
@RequiredArgsConstructor
public class ShipmentService {

    /** status_history 의 상태축 이름. 화면 라벨이 아니라 감사 조회 키라 바꾸면 과거 이력과 끊긴다. */
    private static final String FIELD_PRINTED = "printed";
    private static final String FIELD_ACKNOWLEDGED = "acknowledged";

    private final ShipmentRepository shipmentRepository;
    private final StatusHistoryService statusHistoryService;
    private final CurrentAuditor currentAuditor;

    /**
     * 매출 1건에 대응하는 발송 건을 확보한다(없으면 생성). 근거: UC_TabPages.vb:752 —
     * 레거시도 매출등록 시점에 sendData를 만든다.
     *
     * <p>묶는 단위는 <b>(거래일자 · 거래처 · 학교 · 분류)</b>다. 같은 날 같은 학교로 여러 품목을
     * 등록해도 <b>발송은 한 건</b>이어야 한다 — 물류는 품목이 아니라 상자 단위로 일한다.
     *
     * <p>반품은 대상이 아니다. 들어오는 물건이라 내보낼 작업이 없다.
     */
    @Transactional
    public Shipment ensureFor(Sale sale) {
        String tradeClass = sale.getProduct().getSalesDivision();
        return shipmentRepository.findKey(sale.getSalesDate(), sale.getPartner().getId(),
                        sale.getSchoolCode(), tradeClass)
                .orElseGet(() -> shipmentRepository.save(Shipment.of(
                        tradeClass, sale.getSalesDate(), sale.getPartner(),
                        sale.getSchoolCode(), sale.getSchoolName(), null)));
    }

    /**
     * 작업요청서 출력 처리 → '출력' 표시. 근거: 작업요청서_신청비교.vb:280.
     *
     * <p>재출력해도 최초 시각을 덮지 않는다 — 이 기록의 의미는 "언제 처음 작업지시가 나갔나"다.
     *
     * @return 이번 호출로 실제 표시가 붙었으면 true(이미 출력된 건이면 false)
     */
    @Transactional
    public boolean markPrinted(Long shipmentId) {
        boolean changed = getOrThrow(shipmentId).markPrinted(LocalDateTime.now());
        if (changed) {
            statusHistoryService.record(StatusEntityType.SHIPMENT, shipmentId,
                    FIELD_PRINTED, false, true, null);
        }
        return changed;
    }

    /**
     * 출력 표시를 되돌린다(B-14). 근거: 프론트 회신 2026-09-17 —
     * "되돌리기는 아직 준비 중입니다 — 한번 올린 출력 기록은 내릴 수 없습니다".
     *
     * <p>‼️<b>사유를 반드시 받는다.</b> 이 기록의 의미는 "언제 처음 작업지시가 나갔나"이고,
     * 되돌리기는 그 답을 지우는 행위다. 사유 없이 내릴 수 있게 하면 나중에
     * "이 건은 왜 지시가 안 나간 걸로 되어 있나"에 아무도 답하지 못한다.
     * 값은 {@code shipment}에서 지우되 <b>누가·언제·왜</b>는 {@code status_history}에 남는다.
     *
     * @return 실제로 내려갔으면 true(애초에 출력 전이면 false — 이미 원하는 상태다)
     */
    @Transactional
    public boolean revertPrinted(Long shipmentId, String reason) {
        boolean changed = getOrThrow(shipmentId).revertPrinted();
        if (changed) {
            statusHistoryService.record(StatusEntityType.SHIPMENT, shipmentId,
                    FIELD_PRINTED, true, false, reason);
        }
        return changed;
    }

    /**
     * 작업 확인 표시(B-14). 출력 다음 단계로, 작업결과의 '확인' ○가 된다.
     *
     * <p>재확인해도 최초 시각·처리자를 덮지 않는다 — 출력과 같은 규칙이다.
     *
     * @return 이번 호출로 표시가 붙었으면 true(이미 확인된 건이면 false)
     */
    @Transactional
    public boolean acknowledge(Long shipmentId) {
        boolean changed = getOrThrow(shipmentId)
                .acknowledge(currentAuditor.username(), LocalDateTime.now());
        if (changed) {
            statusHistoryService.record(StatusEntityType.SHIPMENT, shipmentId,
                    FIELD_ACKNOWLEDGED, false, true, null);
        }
        return changed;
    }

    /** 확인 표시를 되돌린다. 출력 되돌리기와 같은 이유로 사유를 받는다. */
    @Transactional
    public boolean revertAcknowledged(Long shipmentId, String reason) {
        boolean changed = getOrThrow(shipmentId).revertAcknowledged();
        if (changed) {
            statusHistoryService.record(StatusEntityType.SHIPMENT, shipmentId,
                    FIELD_ACKNOWLEDGED, true, false, reason);
        }
        return changed;
    }

    /**
     * 물류 발송정보 입력. 박스 수·발송일·발송메모(작업요청서.vb:915)에
     * 26p 확정 항목인 <b>발송구분·수령인·송장</b>을 함께 받는다.
     *
     * <p>보내지 않은 항목은 건드리지 않는다 — 박스 수만 고치려다 발송일이나
     * 수령인이 지워지면 택배가 누구에게 가는지 알 수 없게 된다.
     */
    @Transactional
    public Shipment updateShipping(Long shipmentId, ShippingUpdateRequest req) {
        Shipment s = getOrThrow(shipmentId);
        s.updateShipping(req.boxCount(), req.sentDate(), req.sendMemo());
        s.updateDelivery(req.deliveryType(), req.receiverName(), req.receiverPhone());
        s.updateTracking(req.courierName(), req.trackingNo());
        return s;
    }

    /**
     * 송장번호 엑셀 일괄 등록. 택배사에 넘긴 목록에 송장번호가 채워져 돌아오는 흐름을 그대로 받는다.
     *
     * <p>양식 3컬럼: <b>발송건ID · 택배사 · 송장번호</b>. 발송건ID로 맞추는 이유는,
     * 거래처·학교 이름으로 맞추면 같은 날 같은 학교에 두 건이 나갈 때 어느 쪽인지 가릴 수 없기 때문이다
     * (내보내는 양식에 ID를 넣어 두므로 담당자가 따로 채울 필요가 없다).
     *
     * <p>한 행이 실패해도 나머지는 반영한다 — 택배사 파일에 우리가 모르는 행이 섞여 있다고
     * 전체가 취소되면, 담당자는 어느 줄이 문제인지 모른 채 처음부터 다시 해야 한다.
     * ⚠️택배사 API를 부르지 않는다(연동 여부는 물류팀 인터뷰 회신 대기).
     */
    @Transactional
    public TrackingUploadResponse uploadTracking(MultipartFile file) {
        List<TrackingUploadResponse.Line> lines = new ArrayList<>();
        int updated = 0;
        int failed = 0;

        com.daesung.sales.common.excel.ExcelSheetReader.assertReadable(file);
        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {   // 0행=헤더
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                Long id = longOrNull(row.getCell(0));
                if (id == null) {
                    continue;   // 발송건ID가 숫자가 아니면 설명행/빈행 → 스킵
                }
                int rowNo = r + 1;
                String courier = str(row.getCell(1));
                String tracking = str(row.getCell(2));
                try {
                    if (tracking == null || tracking.isBlank()) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT, "송장번호가 비어 있음");
                    }
                    Shipment s = shipmentRepository.findById(id).orElseThrow(() ->
                            new BusinessException(ErrorCode.NOT_FOUND, "발송 건이 없습니다. id=" + id));
                    s.updateTracking(courier, tracking);
                    updated++;
                    lines.add(new TrackingUploadResponse.Line(rowNo, "UPDATED", id, tracking, null));
                } catch (BusinessException e) {
                    failed++;
                    lines.add(new TrackingUploadResponse.Line(rowNo, "ERROR", id, tracking, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        }
        return new TrackingUploadResponse(updated, failed, lines);
    }

    private static String str(org.apache.poi.ss.usermodel.Cell c) {
        if (c == null) {
            return null;
        }
        return switch (c.getCellType()) {
            case STRING -> c.getStringCellValue().trim();
            // 송장번호가 숫자로 들어오면 지수표기(1.23E11)가 되지 않게 정수로 찍는다
            case NUMERIC -> String.valueOf((long) c.getNumericCellValue());
            default -> null;
        };
    }

    private static Long longOrNull(org.apache.poi.ss.usermodel.Cell c) {
        if (c == null) {
            return null;
        }
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> (long) c.getNumericCellValue();
                case STRING -> Long.parseLong(c.getStringCellValue().trim());
                default -> null;
            };
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private Shipment getOrThrow(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "발송 건이 없습니다. id=" + id));
    }
}
