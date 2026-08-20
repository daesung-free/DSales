package com.daesung.sales.logistics.service;

import com.daesung.sales.logistics.dto.WorkOrderResponse;
import com.daesung.sales.logistics.dto.WorkResultRow;
import com.daesung.sales.logistics.entity.DeliveryType;
import com.daesung.sales.logistics.entity.Shipment;
import com.daesung.sales.logistics.repository.ShipmentRepository;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.sale.dto.ShipmentQtyAgg;
import com.daesung.sales.sale.dto.ShipmentWarehouseAgg;
import com.daesung.sales.warehouse.entity.WarehouseType;
import com.daesung.sales.sale.dto.WorkOrderLineAgg;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 물류 작업요청서 · 작업결과 조회.
 *
 * <p>둘 다 <b>발송 건(shipment)에 매출 수량을 붙여</b> 만든다. 발송 건은 진행 상태(출력·박스·발송일)를,
 * 매출은 내용(무엇을 몇 개)을 갖고 있어 한쪽만으로는 화면이 안 된다.
 * 레거시도 같은 구조다 — sendData에 salesData를 조인한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkService {

    private final ShipmentRepository shipmentRepository;
    private final SaleRepository saleRepository;
    private final CurrentAuditor currentAuditor;

    /** 발송 건을 찾기 위한 키. 학교·분류가 null일 수 있어 빈 문자열로 정규화해 맞춘다. */
    private static String key(LocalDate date, Long partnerId, String schoolCode, String tradeClass) {
        return date + "|" + partnerId + "|" + (schoolCode == null ? "" : schoolCode)
                + "|" + (tradeClass == null ? "" : tradeClass);
    }

    /**
     * 작업결과 — 발송 건별 요약(상품군 수량·박스·발송일).
     *
     * <p>'출력'·'완료'는 레거시처럼 <b>날짜가 채워졌는지</b>로 판단한다. 별도 상태 컬럼이 없다.
     */
    public List<WorkResultRow> workResults(LocalDate from, LocalDate to, String tradeClass,
                                           Long partnerId, Boolean printed, WarehouseType warehouseType) {
        // ★역할별 기본값(발주처 확정 3-2 나): "물류에서 실제 발송하는 상품은 모두 본사물류창고이므로
        //   출고창고는 본사물류창고만 디폴트, 본사 출고담당(관리자)은 전체/본사물류/위탁 선택 가능".
        //   물류는 요청에 무엇을 넣든 본사물류창고로 강제한다 — 프론트도 필터를 관리자 전용으로 만들었고,
        //   두 층의 규칙이 갈리면 화면과 데이터가 어긋난다.
        WarehouseType effectiveType = currentAuditor.hasRole("ADMIN")
                ? warehouseType : WarehouseType.MAIN;
        // 발송 단위별 창고(여러 창고에서 나갔을 수 있다)
        Map<String, List<ShipmentWarehouseAgg>> whByKey = new LinkedHashMap<>();
        for (ShipmentWarehouseAgg a : saleRepository.shipmentWarehouses(from, to)) {
            whByKey.computeIfAbsent(
                    key(a.getTradeDate(), a.getPartnerId(), a.getSchoolCode(), a.getTradeClass()),
                    k -> new ArrayList<>()).add(a);
        }

        Map<String, Map<String, Long>> qtyByKey = new LinkedHashMap<>();
        for (ShipmentQtyAgg a : saleRepository.shipmentQty(from, to)) {
            qtyByKey.computeIfAbsent(
                            key(a.getTradeDate(), a.getPartnerId(), a.getSchoolCode(), a.getTradeClass()),
                            k -> new LinkedHashMap<>())
                    .merge(blankToEtc(a.getTradeClass()), a.getQty(), Long::sum);
        }

        List<WorkResultRow> rows = new ArrayList<>();
        for (Shipment s : shipmentRepository.search(from, to, tradeClass, partnerId, printed, null)) {
            String k = key(s.getTradeDate(), s.getPartner().getId(), s.getSchoolCode(), s.getTradeClass());
            List<ShipmentWarehouseAgg> whs = whByKey.getOrDefault(k, List.of());

            // 창고 필터. 창고가 기록되지 않은 발송(V40 이전 매출)은 필터를 걸면 판단할 수 없어 제외한다 —
            // 남겨두면 "본사물류창고만" 골랐는데 창고 불명 건이 섞여 나온다.
            if (effectiveType != null
                    && whs.stream().noneMatch(w -> w.getWarehouseType() == effectiveType)) {
                continue;
            }

            Map<String, Long> q = qtyByKey.getOrDefault(k, Map.of());
            long total = q.values().stream().mapToLong(Long::longValue).sum();
            String whNames = whs.stream().map(ShipmentWarehouseAgg::getWarehouseName)
                    .distinct().collect(java.util.stream.Collectors.joining(", "));
            rows.add(new WorkResultRow(s.getId(), s.getTradeClass(), s.getTradeDate(), s.getTradeSeq(),
                    s.getPartner().getCode(), s.getPartner().getName(),
                    s.getSchoolCode(), s.getSchoolName(),
                    s.getPrintedAt() != null, s.getCompletedAt() != null, s.getSentDate(),
                    whNames.isEmpty() ? null : whNames,
                    q, total, s.getBoxCount(), s.getSendMemo(), s.getMemo()));
        }
        return rows;
    }

    /** 작업요청서 — 발송 건 + 그 안에 담을 도서 목록(무엇을 몇 개 넣어라). */
    public List<WorkOrderResponse> workOrders(LocalDate from, LocalDate to, String tradeClass,
                                              Long partnerId, Boolean printed,
                                              DeliveryType deliveryType) {
        Map<String, List<WorkOrderResponse.Line>> linesByKey = new LinkedHashMap<>();
        for (WorkOrderLineAgg a : saleRepository.workOrderLines(from, to)) {
            linesByKey.computeIfAbsent(
                            key(a.getTradeDate(), a.getPartnerId(), a.getSchoolCode(), a.getTradeClass()),
                            k -> new ArrayList<>())
                    .add(new WorkOrderResponse.Line(a.getCatCode(), a.getProductCode(), a.getProductName(),
                            a.getBookRound(), a.getUnitPrice(), a.getSupplyRate(),
                            a.getQty(), a.getAmount() == null ? 0 : a.getAmount()));
        }

        List<WorkOrderResponse> out = new ArrayList<>();
        for (Shipment s : shipmentRepository.search(from, to, tradeClass, partnerId, printed, deliveryType)) {
            List<WorkOrderResponse.Line> lines = linesByKey.getOrDefault(
                    key(s.getTradeDate(), s.getPartner().getId(), s.getSchoolCode(), s.getTradeClass()),
                    List.of());
            long total = lines.stream().mapToLong(WorkOrderResponse.Line::qty).sum();
            DeliveryType dt = s.getDeliveryType();
            out.add(new WorkOrderResponse(s.getId(), s.getTradeClass(), s.getTradeDate(),
                    s.getPartner().getCode(), s.getPartner().getName(),
                    s.getSchoolCode(), s.getSchoolName(), s.getPrintedAt() != null,
                    s.getBoxCount(), s.getSentDate(), s.getSendMemo(),
                    dt, (dt == null) ? null : dt.label(),
                    s.getReceiverName(), s.getReceiverPhone(),
                    s.getCourierName(), s.getTrackingNo(),
                    total, lines));
        }
        return out;
    }

    /** 매출구분이 비어 있는 상품은 '기타'로 묶는다 — 빈 키로 두면 화면에 이름 없는 칸이 생긴다. */
    private static String blankToEtc(String tradeClass) {
        return (tradeClass == null || tradeClass.isBlank()) ? "기타" : tradeClass;
    }
}
