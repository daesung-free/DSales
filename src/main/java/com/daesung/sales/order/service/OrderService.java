package com.daesung.sales.order.service;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.dsre.gateway.NewOrder;
import com.daesung.sales.order.dto.OrderCreateRequest;
import com.daesung.sales.order.dto.OrderCreateResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.DsreOrderRow;
import com.daesung.sales.dsre.gateway.OrderState;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    private final com.daesung.sales.common.audit.CurrentAuditor currentAuditor;

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

    // ── 진행상태 수동 전환(자료요청서 3-2(가)) ──────────────────────────────────────

    /**
     * <b>우리가 바꿔도 되는 전이</b>. 정본(자료요청서 3-2(가).진행상태)에서 물류 담당자 몫으로
     * 적힌 것만 담는다 — 나머지(접수·검수·취소)는 order 사이트와 DSRE2 데스크톱 소관이다.
     *
     * <pre>
     *   S → W   거래명세서 출력(자동). 수동으로도 넘길 수 있게 열어 둔다
     *   W → S   ★되돌리기 — "되돌릴 때는 수동 전환"
     *   W → D   발송완료 — "물류가 발송 처리(체크박스 다건 일괄)"
     * </pre>
     *
     * <p>★<b>표에 없는 전이는 거부한다.</b> 아무 상태로나 뛰게 열어 두면 접수완료가 곧바로
     * 발송완료가 되는 주문이 생기고, 그러면 검수·준비 단계가 있으나 마나가 된다.
     * 되돌리기도 <b>한 칸(W→S)</b>만이다 — 발송완료를 되돌리는 경로는 정본에 없다.
     */
    private static final Map<OrderState, Set<OrderState>> ALLOWED = Map.of(
            OrderState.PREPARING, Set.of(OrderState.READY_TO_SHIP),
            OrderState.READY_TO_SHIP, Set.of(OrderState.PREPARING, OrderState.SHIPPED));

    /**
     * 진행상태 <b>다건 일괄</b> 전환. 발송완료 처리와 되돌리기가 같은 경로를 쓴다.
     *
     * <p>★<b>한 건이 안 된다고 전체를 실패시키지 않는다.</b> 100건을 체크한 담당자가
     * 이미 발송완료된 1건 때문에 전부 되돌려 다시 고르는 것은 실무에서 쓸 수 없다.
     * 대신 <b>건별로 사유를 붙여</b> 돌려주고, 요청 전체가 잘못된 경우(우리가 만들 수 없는
     * 목표 상태)만 예외로 막는다 — 그건 데이터 문제가 아니라 화면 오류다.
     *
     * <p>바꾼 건은 {@code status_history}에 남긴다. DSRE2는 제자리 UPDATE라 이전 값이 사라져,
     * 우리가 남기지 않으면 <b>되돌린 사실 자체가 어디에도 없다</b>.
     */
    @Transactional
    public BulkStateResult changeState(List<Integer> reqCds, OrderState to, String reason) {
        if (reqCds == null || reqCds.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "대상 주문이 없습니다.");
        }
        boolean reachable = ALLOWED.values().stream().anyMatch(s -> s.contains(to));
        if (!reachable) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "매출프로그램에서 바꿀 수 없는 상태입니다: " + to.label() + "(" + to.code() + "). "
                            + "접수·검수·취소는 order 사이트와 DSRE2 소관입니다.");
        }
        List<ItemResult> results = new ArrayList<>();
        int changed = 0;
        for (Integer reqCd : new LinkedHashSet<>(reqCds)) {   // 같은 건이 두 번 체크돼도 한 번만
            ItemResult r = changeOne(reqCd, to, reason);
            results.add(r);
            if (r.changed()) {
                changed++;
            }
        }
        return new BulkStateResult(results.size(), changed, results.size() - changed, results);
    }

    private ItemResult changeOne(int reqCd, OrderState to, String reason) {
        DsreOrderRow before = dsreGateway.findOrder(reqCd).orElse(null);
        if (before == null) {
            return skip(reqCd, null, to, "DSRE2에 해당 주문이 없습니다.");
        }
        String fromCode = before.stateCode();
        OrderState from = OrderState.ofCode(fromCode).orElse(null);
        if (from == to) {
            return skip(reqCd, fromCode, to, "이미 " + to.label() + " 상태입니다.");
        }
        if (from == null || !ALLOWED.getOrDefault(from, Set.of()).contains(to)) {
            return skip(reqCd, fromCode, to,
                    OrderState.labelOf(fromCode) + " → " + to.label() + " 전환은 허용되지 않습니다.");
        }
        if (dsreGateway.changeState(reqCd, fromCode, to.code()) == 0) {
            // 조회와 UPDATE 사이에 DSRE2 데스크톱이 먼저 옮겼다. 성공으로 보고하면 담당자가 속는다.
            return skip(reqCd, fromCode, to, "그 사이 다른 곳에서 상태가 바뀌었습니다. 새로고침 후 다시 시도하세요.");
        }
        // ★사유가 비어 있어도 기록은 남긴다 — 되돌린 사실이 사라지는 것보다 낫다.
        String why = (reason == null || reason.isBlank())
                ? (to == OrderState.PREPARING ? "수동 되돌리기(사유 미입력)" : "수동 전환(사유 미입력)")
                : reason;
        statusHistoryService.record(StatusEntityType.DSRE_ORDER, (long) reqCd, "STATE",
                fromCode, to.code(), why);
        return new ItemResult(reqCd, true, fromCode, OrderState.labelOf(fromCode),
                to.code(), to.label(), null);
    }

    private static ItemResult skip(int reqCd, String fromCode, OrderState to, String message) {
        return new ItemResult(reqCd, false, fromCode, OrderState.labelOf(fromCode),
                to.code(), to.label(), message);
    }

    /** 건별 전환 결과. {@code changed=false}면 {@code message}에 넘어간 이유가 있다. */
    public record ItemResult(int reqCd, boolean changed,
                             String fromCode, String fromName,
                             String toCode, String toName, String message) {
    }

    /** 일괄 전환 결과. {@code skipped}가 0이 아니면 건별 사유를 확인해야 한다. */
    public record BulkStateResult(int requested, int changed, int skipped, List<ItemResult> results) {
    }

    /**
     * 신규 주문 등록. 근거: 레거시 특약점 사이트 {@code Application_SQL.xml:266~299}를 그대로 옮겼다.
     *
     * <p>★<b>등록 직후 상태는 항상 A(접수완료)</b>다. 레거시도 STATE를 넣지 않고 DB 기본값에 맡긴다 —
     * 우리가 임의로 다른 상태로 만들면 그 뒤 전이(G→S→W→D)를 밟는 DSRE2 데스크톱과 어긋난다.
     *
     * <p>★<b>신청방식은 우리가 고르지 않는다.</b> 간편신청을 받는지는 시행 마스터의
     * {@code EASY_GN}에 이미 정해져 있다. 여기서는 <b>들어온 값이 앞뒤가 맞는지만</b> 본다 —
     * 간편인데 인원이 하나도 없거나, 과목신청인데 수량이 없으면 거부한다.
     * 조용히 통과시키면 물류가 무엇을 몇 개 보낼지 알 수 없는 주문이 선다.
     */
    @Transactional
    public OrderCreateResponse create(OrderCreateRequest req) {
        List<NewOrder.ClassLine> classes = new ArrayList<>();
        int subjectLines = 0;
        long totalQty = 0;

        for (int i = 0; i < req.classes().size(); i++) {
            OrderCreateRequest.ClassLine c = req.classes().get(i);
            boolean hasSubjects = c.subjects() != null && !c.subjects().isEmpty();
            // 미지정이면 과목 수량 유무로 정한다(레거시 신청 화면과 같은 판정).
            String applyType = (c.applyType() != null) ? c.applyType() : (hasSubjects ? "N" : "S");

            long headCount = nz(c.humanities()) + nz(c.science()) + nz(c.combined());
            if ("S".equals(applyType)) {
                if (headCount <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            (i + 1) + "번째 반(" + c.className() + "): 간편신청인데 인원이 없습니다. "
                                    + "인문·자연·통합 중 하나는 1명 이상이어야 합니다.");
                }
                totalQty += headCount;
            } else {
                if (!hasSubjects) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            (i + 1) + "번째 반(" + c.className() + "): 과목신청인데 과목 수량이 없습니다.");
                }
                for (OrderCreateRequest.SubjectQty q : c.subjects()) {
                    totalQty += q.qty();
                }
                subjectLines += c.subjects().size();
            }

            List<NewOrder.SubjectQty> subjects = new ArrayList<>();
            if (hasSubjects) {
                c.subjects().forEach(q -> subjects.add(new NewOrder.SubjectQty(q.resCd(), q.qty())));
            }
            classes.add(new NewOrder.ClassLine(c.className(), applyType,
                    c.humanities(), c.science(), c.combined(), subjects));
        }

        NewOrder order = new NewOrder(req.dtlCd(), req.custCode(), req.schoolCode(),
                // ★procYn 은 **비운 채로 넘긴다** — 미지정이면 시행 기본값을 쓰는 게 스펙이고,
                //   그 기본값은 DSRE2 시행 마스터에 있다(게이트웨이 SQL 의 COALESCE).
                //   여기서 'N'으로 박았더니 성적처리(Y) 시행이 비처리로 저장됐다(2026-09-18 지적).
                req.procYn(), orDefault(req.procYn2(), "N"), req.procDate(),
                req.teacher(), req.tel(), req.email(), req.zipCode(), req.address(), req.memo(),
                orDefault(req.deliveryGubun(), "H"), classes);

        int reqCd = dsreGateway.createOrder(order, currentAuditor.username());

        // ‼️DSRE2엔 상태 이력 테이블이 없다(제자리 UPDATE라 이전 값이 사라진다).
        //   우리가 만든 주문만이라도 "누가 언제 넣었나"를 남긴다 — 상태전이 기록과 같은 자리다.
        statusHistoryService.record(StatusEntityType.DSRE_ORDER, (long) reqCd, "state",
                null, OrderState.RECEIVED.code(),
                "주문 등록(시행 " + req.dtlCd() + " · 거래처 " + req.custCode()
                        + " · 반 " + classes.size() + "개 · 수량 " + totalQty + ")");

        return new OrderCreateResponse(reqCd, OrderState.RECEIVED.code(), OrderState.RECEIVED.label(),
                classes.size(), subjectLines, totalQty);
    }

    private static long nz(Integer v) {
        return (v == null) ? 0 : v;
    }

    private static String orDefault(String v, String fallback) {
        return (v == null || v.isBlank()) ? fallback : v;
    }

}
