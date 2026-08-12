package com.daesung.sales.logistics.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.logistics.entity.Shipment;
import com.daesung.sales.logistics.repository.ShipmentRepository;
import com.daesung.sales.sale.entity.Sale;
import java.time.LocalDate;
import java.time.LocalDateTime;
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

    private final ShipmentRepository shipmentRepository;

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
        return getOrThrow(shipmentId).markPrinted(LocalDateTime.now());
    }

    /** 물류 발송정보 입력(박스 수·발송일·발송메모). 근거: 작업요청서.vb:915. */
    @Transactional
    public Shipment updateShipping(Long shipmentId, Integer boxCount, LocalDate sentDate, String sendMemo) {
        Shipment s = getOrThrow(shipmentId);
        s.updateShipping(boxCount, sentDate, sendMemo);
        return s;
    }

    private Shipment getOrThrow(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "발송 건이 없습니다. id=" + id));
    }
}
