package com.daesung.sales.order.service;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.DsreOrderRow;
import com.daesung.sales.dsre.gateway.OrderState;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주문 진행상태 처리. 상태 원본은 DSRE2 {@code tbl_request_info.STATE}이고 우리는 그 값을 직접 고친다
 * (복사본을 두지 않으므로 동기화가 없다 — DSRE2 데스크톱이 바로 그 값을 읽는다).
 *
 * <p>전환은 <b>거래명세서 발급 하나만</b> 우리가 한다. 나머지 전이(검수·준비중·발송완료·취소)는
 * DSRE2 데스크톱 소관이다(발주처 확정 2026-08-11 "DSRE는 그대로 사용").
 * 명세서만 예외인 이유는 명세서 출력이 원래부터 매출프로그램 기능이고(레거시 리포트 21종),
 * 발주처가 "거래명세서를 출력 처리한 시점(자동 전환)"으로 정했기 때문이다(자료요청서 3-2(가) 5번).
 */
@Service
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class OrderService {

    private final DsreGateway dsreGateway;
    private final StatusHistoryService statusHistoryService;

    /**
     * 거래명세서 발급 처리 → 발송준비중(W).
     *
     * <p>DSRE2 쪽은 조건부 UPDATE(상품준비중일 때만)라 재출력해도 상태가 되돌아가지 않는다.
     * 이미 W거나 그 이후면 <b>오류가 아니라</b> 바뀐 것 없음으로 응답한다 —
     * 명세서를 두 번 뽑는 건 실무에서 흔한 일이라 실패로 막을 이유가 없다.
     *
     * <p>DSRE2에는 상태 이력 테이블이 없어 제자리 UPDATE로 이전 값이 사라진다.
     * 그래서 우리가 바꾼 건만이라도 status_history에 남긴다.
     */
    @Transactional
    public IssueResult issueStatement(int reqCd) {
        DsreOrderRow before = dsreGateway.findOrder(reqCd)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "DSRE2에 해당 주문이 없습니다. reqCd=" + reqCd));

        String fromCode = before.stateCode();
        int changed = dsreGateway.markReadyToShip(reqCd);
        if (changed == 0) {
            // 상품준비중이 아니어서 안 바뀐 것. 어떤 상태여서 넘어갔는지 그대로 알려준다.
            return new IssueResult(reqCd, false, fromCode, OrderState.labelOf(fromCode),
                    "상품준비중 상태가 아니어서 진행상태를 바꾸지 않았습니다.");
        }

        statusHistoryService.record(StatusEntityType.DSRE_ORDER, (long) reqCd, "STATE",
                fromCode, OrderState.READY_TO_SHIP.code(), "거래명세서 발급");

        return new IssueResult(reqCd, true,
                OrderState.READY_TO_SHIP.code(), OrderState.READY_TO_SHIP.label(),
                "거래명세서 발급으로 발송준비중으로 전환했습니다.");
    }

    /** 발급 처리 결과. */
    public record IssueResult(int reqCd, boolean changed, String stateCode, String stateName, String message) {
    }
}
