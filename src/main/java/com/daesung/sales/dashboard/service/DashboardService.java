package com.daesung.sales.dashboard.service;

import com.daesung.sales.dashboard.dto.DashboardResponse;
import com.daesung.sales.dashboard.dto.TargetRequest;
import com.daesung.sales.dashboard.dto.TargetResponse;
import com.daesung.sales.dashboard.entity.DashboardSnapshot;
import com.daesung.sales.dashboard.entity.SalesTarget;
import com.daesung.sales.dashboard.entity.TargetEntryType;
import com.daesung.sales.dashboard.entity.TargetScope;
import com.daesung.sales.dashboard.repository.DashboardSnapshotRepository;
import com.daesung.sales.dashboard.repository.SalesTargetRepository;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 매출 대시보드. 목표 등록(upsert) + 목표 대비 실적·달성률·전년비 집계. */
@Service
@RequiredArgsConstructor
public class DashboardService {

    private final SalesTargetRepository targetRepository;
    private final SaleRepository saleRepository;
    private final DashboardSnapshotRepository snapshotRepository;

    /** 매출목표 등록/수정(같은 연·월·대상·종류면 금액 갱신). */
    @Transactional
    public void upsertTarget(TargetRequest req) {
        TargetScope scope = req.scopeOrDefault();
        TargetEntryType type = req.entryTypeOrDefault();
        String scopeKey = (scope == TargetScope.DIVISION) ? req.scopeKey() : null;
        Long productId = (scope == TargetScope.PRODUCT) ? req.productId() : null;

        targetRepository.find(req.year(), req.month(), scope, scopeKey, productId, type)
                .ifPresentOrElse(
                        t -> t.updateAmount(req.targetAmount()),
                        () -> targetRepository.save(SalesTarget.create(
                                req.year(), req.month(), scope, scopeKey, productId,
                                type, req.targetAmount())));
    }

    /** 연도의 목표 목록(관리 화면). */
    @Transactional(readOnly = true)
    public List<TargetResponse> listTargets(int year) {
        return targetRepository.findByFiscalYearOrderByScopeAscScopeKeyAscMonthAsc(year).stream()
                .map(TargetResponse::from).toList();
    }

    /**
     * 목표 대비 실적 대시보드(월별 + 연간). 실적=순매출(매출−반품), 전년 동기 대비.
     *
     * <p>연간 목표만 등록된 경우(발주처는 연 1회 연간으로 준다) 월 셀 목표는 비고
     * <b>연간 요약에만</b> 그 값이 잡힌다 — 연간 금액을 12로 나눠 월에 뿌리면 있지도 않은
     * 월 목표를 만들어내는 셈이라, 달성률이 실제와 다르게 보인다.
     *
     * <p>전년 실적은 우리 매출에서 계산하되, 그 연도 매출이 아예 없으면(이관하지 않아 비어 있는
     * 과거연도) 저장된 ACTUAL 값으로 채운다.
     */
    @Transactional(readOnly = true)
    public DashboardResponse salesDashboard(int year, TargetScope scope, String scopeKey, Long productId) {
        // scope 미지정이면 넘어온 값에서 추론한다 — 기존처럼 productId만 넘기던 호출이
        // 조용히 전사 대시보드로 바뀌면 안 된다.
        TargetScope sc = scope;
        if (sc == null) {
            sc = (productId != null) ? TargetScope.PRODUCT
                    : (scopeKey != null && !scopeKey.isBlank()) ? TargetScope.DIVISION : TargetScope.COMPANY;
        }
        String key = (sc == TargetScope.DIVISION) ? scopeKey : null;
        Long pid = (sc == TargetScope.PRODUCT) ? productId : null;

        Map<Integer, Long> monthTargets = new HashMap<>();
        Long annualTarget = null;
        for (SalesTarget t : targetRepository.findByYearAndScope(year, sc, key, pid)) {
            if (t.getEntryType() != TargetEntryType.TARGET) {
                continue;
            }
            if (t.isAnnual()) {
                annualTarget = t.getTargetAmount();
            } else {
                monthTargets.put(t.getMonth(), t.getTargetAmount());
            }
        }

        Map<Integer, Long> actual = monthlyMap(year, sc, pid);
        Map<Integer, Long> prev = monthlyMap(year - 1, sc, pid);

        List<DashboardResponse.MonthCell> months = new ArrayList<>();
        long tTarget = 0;
        long tActual = 0;
        long tPrev = 0;
        for (int m = 1; m <= 12; m++) {
            long target = monthTargets.getOrDefault(m, 0L);
            long act = actual.getOrDefault(m, 0L);
            long pv = prev.getOrDefault(m, 0L);
            months.add(new DashboardResponse.MonthCell(m, target, act,
                    pct(act, target), pv, growth(act, pv)));
            tTarget += target;
            tActual += act;
            tPrev += pv;
        }

        // 연간 목표가 등록돼 있으면 월 합계보다 그 값이 우선이다(월별을 안 넣고 연간만 넣는 운영).
        long yearTarget = (annualTarget != null) ? annualTarget : tTarget;
        // 전년 매출이 통째로 비어 있으면 저장해둔 확정 실적으로 대체.
        long yearPrev = (tPrev > 0) ? tPrev : storedActual(year - 1, sc, key, pid);

        DashboardResponse.YearSummary summary = new DashboardResponse.YearSummary(
                yearTarget, tActual, pct(tActual, yearTarget), yearPrev, growth(tActual, yearPrev));
        return new DashboardResponse(year, sc, key, pid, computedAt(year, sc), months, summary);
    }

    /** 저장된 확정 실적(ACTUAL) 합. 이관하지 않은 과거연도의 전년비를 살리는 용도. */
    private long storedActual(int year, TargetScope scope, String scopeKey, Long productId) {
        return targetRepository.findByYearAndScope(year, scope, scopeKey, productId).stream()
                .filter(t -> t.getEntryType() == TargetEntryType.ACTUAL)
                .mapToLong(SalesTarget::getTargetAmount)
                .sum();
    }

    /**
     * 월별 순매출. <b>스냅샷이 있으면 그것을, 없으면 실시간 계산</b>한다.
     *
     * <p>발주처 확정(3-2 아)대로 새벽 배치가 미리 계산해 두지만, 배치가 안 돌았거나 실패한 날
     * 화면이 통째로 비면 "느린 것"보다 나쁘다. 그래서 폴백을 둔다.
     * 대신 {@link #computedAt}로 언제 기준 숫자인지 화면에 드러낸다.
     */
    private Map<Integer, Long> monthlyMap(int year, TargetScope scope, Long productId) {
        if (scope == TargetScope.COMPANY) {
            List<DashboardSnapshot> snaps =
                    snapshotRepository.findByYearAndScope(year, TargetScope.COMPANY, null);
            if (!snaps.isEmpty()) {
                Map<Integer, Long> map = new HashMap<>();
                snaps.forEach(s -> map.put(s.getMonth(), s.getNetSales()));
                return map;
            }
        }
        return realtimeMonthlyMap(year, productId);
    }

    private Map<Integer, Long> realtimeMonthlyMap(int year, Long productId) {
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] r : saleRepository.monthlyNetSales(year, productId)) {
            map.put(((Number) r[0]).intValue(), ((Number) r[1]).longValue());
        }
        return map;
    }

    /** 스냅샷 기준시각(가장 최근). 스냅샷을 안 썼으면 null — 화면이 "실시간"임을 알 수 있다. */
    private LocalDateTime computedAt(int year, TargetScope scope) {
        if (scope != TargetScope.COMPANY) {
            return null;
        }
        return snapshotRepository.findByYearAndScope(year, TargetScope.COMPANY, null).stream()
                .map(DashboardSnapshot::getComputedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);
    }

    /** 달성률 = 실적/목표 ×100(소수1). 목표 0이면 null. */
    private static Double pct(long actual, long target) {
        return (target == 0) ? null : Math.round((double) actual / target * 100 * 10) / 10.0;
    }

    /** 전년비 성장률 = (실적−전년)/전년 ×100(소수1). 전년 0이면 null. */
    private static Double growth(long actual, long prev) {
        return (prev == 0) ? null : Math.round((double) (actual - prev) / prev * 100 * 10) / 10.0;
    }
}
