package com.daesung.sales.dashboard.service;

import com.daesung.sales.consignment.repository.ConsignmentOutRepository;
import com.daesung.sales.dashboard.dto.ActionItemResponse;
import com.daesung.sales.logistics.repository.ShipmentRepository;
import com.daesung.sales.partner.dto.CollateralExpiryResponse;
import com.daesung.sales.partner.service.PartnerService;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 <b>할 일</b> 목록. 지금 손대야 할 것 세 가지를 한 번에 센다 —
 * 위탁 정산 대기 · 미확인 출고요청 · 담보 만기 임박.
 *
 * <p>★<b>0건이면 카드를 만들지 않는다.</b> "0건" 카드가 늘 떠 있으면 담당자가 매번
 * 숫자를 읽어 "할 일이 없구나"를 확인해야 한다. 목록에 있다는 것 자체가 신호여야 한다.
 *
 * <p>★각 숫자는 <b>해당 화면이 쓰는 것과 같은 판정</b>을 쓴다. 대시보드만 따로 세면
 * 카드에는 3건인데 화면을 열면 5건인 상황이 생기고, 그때 담당자는 둘 다 못 믿게 된다.
 */
@Service
@RequiredArgsConstructor
public class ActionItemService {

    /** 담보 만기 임박 기준(일). 25p 화면·알림 배치와 같은 값이라야 숫자가 갈리지 않는다. */
    private static final int COLLATERAL_WITHIN_DAYS = 30;

    /**
     * 미확인 출고요청을 거슬러 세는 기간(일).
     * 무한정 거슬러 세면 몇 년 전 미출력 건까지 잡혀 숫자가 늘 커져 있고,
     * 그러면 아무도 안 본다. 최근 것만 "지금 할 일"이다.
     */
    private static final int WORK_ORDER_LOOKBACK_DAYS = 30;

    private final ConsignmentOutRepository consignmentOutRepository;
    private final ShipmentRepository shipmentRepository;
    private final PartnerService partnerService;

    @Transactional(readOnly = true)
    public List<ActionItemResponse> actionItems() {
        LocalDate today = LocalDate.now();
        List<ActionItemResponse> items = new ArrayList<>();

        // 1) 위탁 정산 대기 — 미결이 남아 있는 건수와 잔여 수량
        int pendingCount = (int) consignmentOutRepository.countPending();
        long pendingQty = consignmentOutRepository.sumPendingQty();
        if (pendingCount > 0) {
            items.add(new ActionItemResponse("settle", "위탁 정산 대기", pendingCount,
                    "/sales/entry", ActionItemResponse.WARNING,
                    "미결잔여 " + String.format("%,d", pendingQty) + "부"));
        }

        // 2) 미확인 출고요청 — 아직 작업지시가 나가지 않은(미출력) 발송 건
        int newReq = newWorkOrderCount(today);
        if (newReq > 0) {
            items.add(new ActionItemResponse("workreq", "미확인 출고요청", newReq,
                    "/logistics/work-request", ActionItemResponse.INFO, "물류 작업요청서"));
        }

        // 3) 담보 만기 임박 — 이미 만료된 건이 섞여 있으면 '임박'이 아니라 사고에 가깝다
        CollateralExpiryResponse collateral =
                partnerService.collateralExpiry(today, COLLATERAL_WITHIN_DAYS);
        int total = collateral.rows().size();
        if (total > 0) {
            long expired = collateral.rows().stream()
                    .filter(r -> "EXPIRED".equals(r.status()))
                    .count();
            items.add(new ActionItemResponse("collateral", "담보 만기 임박", total,
                    "/closing/receivable-status",
                    (expired > 0) ? ActionItemResponse.DANGER : ActionItemResponse.WARNING,
                    (expired > 0) ? ("만료 " + expired + "건 포함") : (COLLATERAL_WITHIN_DAYS + "일 이내")));
        }
        return items;
    }

    /** 미확인(미출력) 출고요청 건수. 작업요청서 화면의 {@code printed=false} 필터와 같은 판정. */
    @Transactional(readOnly = true)
    public int newWorkOrderCount(LocalDate asOf) {
        LocalDate to = (asOf != null) ? asOf : LocalDate.now();
        return shipmentRepository.countUnprinted(to.minusDays(WORK_ORDER_LOOKBACK_DAYS), to);
    }
}
