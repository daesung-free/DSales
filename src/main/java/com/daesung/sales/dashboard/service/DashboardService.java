package com.daesung.sales.dashboard.service;

import com.daesung.sales.dashboard.dto.DashboardResponse;
import com.daesung.sales.dashboard.dto.TargetRequest;
import com.daesung.sales.dashboard.entity.SalesTarget;
import com.daesung.sales.dashboard.repository.SalesTargetRepository;
import com.daesung.sales.sale.repository.SaleRepository;
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

    /** 매출목표 등록/수정(같은 연·월·상품 있으면 금액 갱신). */
    @Transactional
    public void upsertTarget(TargetRequest req) {
        targetRepository.find(req.year(), req.month(), req.productId())
                .ifPresentOrElse(
                        t -> t.updateAmount(req.targetAmount()),
                        () -> targetRepository.save(SalesTarget.create(
                                req.year(), req.month(), req.productId(), req.targetAmount())));
    }

    /** 목표 대비 실적 대시보드(월별 + 연간). 실적=순매출(매출−반품), 전년 동기 대비. */
    @Transactional(readOnly = true)
    public DashboardResponse salesDashboard(int year, Long productId) {
        Map<Integer, Long> targets = new HashMap<>();
        targetRepository.findByYear(year, productId)
                .forEach(t -> targets.put(t.getMonth(), t.getTargetAmount()));
        Map<Integer, Long> actual = monthlyMap(year, productId);
        Map<Integer, Long> prev = monthlyMap(year - 1, productId);

        List<DashboardResponse.MonthCell> months = new ArrayList<>();
        long tTarget = 0, tActual = 0, tPrev = 0;
        for (int m = 1; m <= 12; m++) {
            long target = targets.getOrDefault(m, 0L);
            long act = actual.getOrDefault(m, 0L);
            long pv = prev.getOrDefault(m, 0L);
            months.add(new DashboardResponse.MonthCell(m, target, act,
                    pct(act, target), pv, growth(act, pv)));
            tTarget += target; tActual += act; tPrev += pv;
        }
        DashboardResponse.YearSummary summary = new DashboardResponse.YearSummary(
                tTarget, tActual, pct(tActual, tTarget), tPrev, growth(tActual, tPrev));
        return new DashboardResponse(year, productId, months, summary);
    }

    private Map<Integer, Long> monthlyMap(int year, Long productId) {
        Map<Integer, Long> map = new HashMap<>();
        for (Object[] r : saleRepository.monthlyNetSales(year, productId)) {
            map.put(((Number) r[0]).intValue(), ((Number) r[1]).longValue());
        }
        return map;
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
