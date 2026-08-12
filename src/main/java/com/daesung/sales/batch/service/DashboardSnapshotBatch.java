package com.daesung.sales.batch.service;

import com.daesung.sales.batch.entity.BatchJobRun;
import com.daesung.sales.dashboard.entity.DashboardSnapshot;
import com.daesung.sales.dashboard.entity.TargetScope;
import com.daesung.sales.dashboard.repository.DashboardSnapshotRepository;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 대시보드 실적 스냅샷 배치. 근거: 발주처 확정(자료요청서 3-2 아) —
 * "③ 하루 1회 갱신(예: 매일 새벽)".
 *
 * <p>대시보드는 매출 전체를 월별로 합산하는 화면이라 조회할 때마다 계산하면 느려진다.
 * 발주처가 그 신선도면 충분하다고 확정해, 새벽에 미리 계산해 둔다.
 *
 * <p><b>대상 연도</b>는 올해와 작년이다. 대시보드가 전년 동월 대비를 함께 보여주기 때문에
 * 작년 값도 필요하고, 그보다 이전은 화면에서 쓰지 않는다.
 */
@Service
@RequiredArgsConstructor
public class DashboardSnapshotBatch {

    public static final String JOB_NAME = "DASHBOARD_SNAPSHOT";

    private static final int MAX_ATTEMPTS = 3;

    private final SaleRepository saleRepository;
    private final DashboardSnapshotRepository snapshotRepository;
    private final BatchJobRunner runner;

    /**
     * 매일 새벽 4시. 담보만기 배치(3시)와 겹치지 않게 한 시간 뒤에 둔다 —
     * 둘 다 무거운 조회라 같은 시각에 돌면 서로 느려진다.
     */
    @Scheduled(cron = "0 0 4 * * *")
    public void scheduled() {
        run(LocalDate.now());
    }

    /** 수동 실행(방금 넣은 매출을 바로 반영하고 싶을 때·검수 시연용). 같은 날 다시 돌려도 값만 갱신된다. */
    public BatchJobRun run(LocalDate baseDate) {
        return runner.run(JOB_NAME, baseDate, MAX_ATTEMPTS, () -> rebuild(baseDate));
    }

    /**
     * 올해·작년의 월별 순매출을 다시 계산해 저장한다. 반환값은 갱신된 행 수.
     *
     * <p>전체를 지우고 다시 넣지 않고 <b>있으면 갱신·없으면 생성</b>한다.
     * 지웠다 넣는 사이에 조회가 들어오면 화면이 순간적으로 빈다.
     */
    @Transactional
    public int rebuild(LocalDate baseDate) {
        LocalDateTime now = LocalDateTime.now();
        int touched = 0;
        for (int year : List.of(baseDate.getYear(), baseDate.getYear() - 1)) {
            touched += rebuildCompany(year, now);
        }
        return touched;
    }

    /** 전사 축. 상품별 축은 상품 수만큼 행이 늘어 조회 시 실시간 계산으로 충분하다(대시보드 기본은 전사다). */
    private int rebuildCompany(int year, LocalDateTime now) {
        List<DashboardSnapshot> toSave = new ArrayList<>();
        for (Object[] r : saleRepository.monthlyNetSales(year, null)) {
            int month = ((Number) r[0]).intValue();
            long net = ((Number) r[1]).longValue();
            DashboardSnapshot s = snapshotRepository
                    .findOne(year, month, TargetScope.COMPANY, null)
                    .orElseGet(() -> DashboardSnapshot.of(year, month, TargetScope.COMPANY, null, net, now));
            s.refresh(net, now);
            toSave.add(s);
        }
        snapshotRepository.saveAll(toSave);
        return toSave.size();
    }
}
