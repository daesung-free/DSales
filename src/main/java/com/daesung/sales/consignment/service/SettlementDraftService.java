package com.daesung.sales.consignment.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.consignment.dto.SettlementDraftRequest;
import com.daesung.sales.consignment.dto.SettlementDraftResponse;
import com.daesung.sales.consignment.entity.ConsignmentOut;
import com.daesung.sales.consignment.entity.SettlementDraft;
import com.daesung.sales.consignment.entity.SettlementDraftLine;
import com.daesung.sales.consignment.repository.ConsignmentOutRepository;
import com.daesung.sales.consignment.repository.SettlementDraftRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.service.PartnerSupplyRateService;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 위탁정산 <b>임시저장(초안)</b>. 근거: 정본 13p "매출등록은 [임시저장] → [매출확정등록] 2단계" +
 * 프론트가 이미 서버 API로 호출 중(`saveSettlementDraft`/`fetchSettlementDrafts`).
 *
 * <p>★<b>확정하지 않는다</b> — 미결 잔여도, 재고도, 매출도 건드리지 않는다.
 * 그래서 {@link com.daesung.sales.closing.service.PeriodLockService 월마감 검사도 하지 않는다}.
 * 마감된 달로 초안을 만들어 두는 것 자체는 장부에 아무 영향이 없고,
 * 실제로 막아야 할 지점은 <b>확정</b>이다(거기서는 이미 막고 있다).
 *
 * <p>★그래도 <b>초과정산은 저장 시점에 거른다.</b> 확정 때만 검사하면 담당자가
 * 다 채워 저장한 뒤 확정 버튼에서야 거절당한다 — 화면도 같은 검증을 저장·확정 양쪽에서
 * 공유하고 있다(프론트 {@code buildLines} 주석).
 * 다만 <b>확정 시점의 검사가 정본</b>이다. 저장 이후 다른 담당자가 같은 미결을 정산하면
 * 초안의 수량이 잔여를 넘게 될 수 있고, 그건 확정에서 걸린다.
 */
@Service
@RequiredArgsConstructor
public class SettlementDraftService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final SettlementDraftRepository draftRepository;
    private final ConsignmentOutRepository consignmentOutRepository;
    private final PartnerSupplyRateService partnerSupplyRateService;
    private final SequenceService sequenceService;
    private final CurrentAuditor currentAuditor;

    @Transactional
    public SettlementDraftResponse save(SettlementDraftRequest req) {
        String draftNo = "DRAFT-" + req.salesDate().format(YYYYMMDD)
                + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);
        SettlementDraft draft = SettlementDraft.create(draftNo, req.salesDate(), req.memo());

        // ★같은 미결이 여러 줄로 들어오면 합쳐서 잔여와 비교한다.
        //   줄마다 따로 보면 각각은 통과해도 합이 잔여를 넘는다.
        Map<Long, Integer> qtyByPending = new HashMap<>();
        for (SettlementDraftRequest.Item i : req.input()) {
            qtyByPending.merge(i.pendingId(), i.settleQty(), Integer::sum);
        }

        for (SettlementDraftRequest.Item item : req.input()) {
            ConsignmentOut co = consignmentOutRepository.findById(item.pendingId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "미결(위탁출고)이 없습니다. id=" + item.pendingId()));

            int total = qtyByPending.get(item.pendingId());
            if (total > co.getRemainingQty()) {
                throw new BusinessException(ErrorCode.OVER_SETTLEMENT,
                        "정산수량이 미결 잔여를 초과했습니다: 잔여 " + co.getRemainingQty()
                                + ", 정산 " + total + " (미결 " + co.getSourceOutNo() + ")");
            }

            Product product = co.getProduct();
            int unitPrice = (item.unitPrice() != null) ? item.unitPrice() : priceOf(product);
            int supplyRate = (item.supplyRate() != null) ? item.supplyRate()
                    : rateOf(product, co.getPartner().getId());
            Integer discount = partnerSupplyRateService.discountFor(product, co.getPartner().getId());
            Amounts amt = Amounts.of(unitPrice, supplyRate, item.settleQty(),
                    product.isTaxFree(), item.tax(), discount);

            draft.addLine(SettlementDraftLine.of(co, item.settleQty(), unitPrice, supplyRate,
                    amt.supplyAmount(), amt.tax()));
        }
        return SettlementDraftResponse.from(draftRepository.save(draft));
    }

    /** 초안 목록(최근 저장순). 전부 매출 미반영이다. */
    @Transactional(readOnly = true)
    public List<SettlementDraftResponse> list() {
        return draftRepository.findAllWithLines().stream()
                .map(SettlementDraftResponse::from)
                .toList();
    }

    /** 초안 삭제(논리삭제). 확정에 쓰인 초안도 이걸로 정리된다. */
    @Transactional
    public void delete(String draftId) {
        SettlementDraft d = draftRepository.findByDraftNo(draftId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "임시저장이 없습니다. draftId=" + draftId));
        d.markDeleted(currentAuditor.username());
    }

    /**
     * 확정에 쓰인 초안 정리. 없는 번호면 <b>조용히 넘어간다</b> —
     * 매출은 이미 확정됐는데 초안이 없다고 전체를 되돌리면 손해가 더 크다.
     * 반드시 호출자 트랜잭션 안에서.
     */
    public void consume(String draftId) {
        if (draftId == null || draftId.isBlank()) {
            return;
        }
        draftRepository.findByDraftNo(draftId)
                .ifPresent(d -> d.markDeleted(currentAuditor.username()));
    }

    /**
     * 공급률 — 입력값이 없으면 거래처×대분류 매핑, 그것도 없으면 도서 기본공급률.
     * 어디에도 없으면 <b>0으로 넘어가지 않고 거절한다</b>: 0%는 "무상"이라는 뜻이 되어
     * 초안이 조용히 0원짜리로 저장된다.
     */
    private int rateOf(Product product, Long partnerId) {
        Integer mapped = partnerSupplyRateService.rateFor(product, partnerId);
        if (mapped != null) {
            return mapped;
        }
        if (product.getSupplyRate() != null) {
            return product.getSupplyRate();
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT,
                "공급률이 없습니다. 입력하거나 거래처별 단가를 등록하세요: " + product.getCode());
    }

    private static int priceOf(Product product) {
        if (product.getPrice() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "정가가 없습니다. 도서 마스터를 확인하세요: " + product.getCode());
        }
        return product.getPrice();
    }
}
