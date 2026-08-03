package com.daesung.sales.batch.service;

import com.daesung.sales.batch.entity.BatchJobRun;
import com.daesung.sales.batch.entity.Notification;
import com.daesung.sales.batch.repository.NotificationRepository;
import com.daesung.sales.partner.dto.CollateralExpiryResponse;
import com.daesung.sales.partner.service.PartnerService;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 담보만기 1개월 전 알림 배치(25p).
 * 근거: 개발문서 2탭 25.0 — 재무팀 인터뷰로 부활한 확정 요구사항("기존에도 있던 기능").
 *
 * <p>기존 조회 API({@link PartnerService#collateralExpiry})를 그대로 재사용한다 —
 * 화면과 배치가 서로 다른 기준으로 만기를 판정하면 "팝업엔 떴는데 조회엔 없다"가 나온다.
 */
@Service
@RequiredArgsConstructor
public class CollateralExpiryBatch {

    public static final String JOB_NAME = "COLLATERAL_EXPIRY";
    public static final String TYPE = "COLLATERAL_EXPIRY";

    /** 25p 요구사항: 만기 1개월 전 알림. */
    private static final int WITHIN_DAYS = 30;
    private static final int MAX_ATTEMPTS = 3;

    private final PartnerService partnerService;
    private final NotificationRepository notificationRepository;
    private final BatchJobRunner runner;

    /** 매일 새벽 3시. */
    @Scheduled(cron = "0 0 3 * * *")
    public void scheduled() {
        run(LocalDate.now());
    }

    /** 수동 실행(운영 재실행·검수 시연용). 같은 날 다시 돌려도 알림은 늘지 않는다. */
    public BatchJobRun run(LocalDate baseDate) {
        return runner.run(JOB_NAME, baseDate, MAX_ATTEMPTS, () -> createNotifications(baseDate));
    }

    @Transactional
    public int createNotifications(LocalDate baseDate) {
        CollateralExpiryResponse target = partnerService.collateralExpiry(baseDate, WITHIN_DAYS);
        int created = 0;
        for (CollateralExpiryResponse.Row row : target.rows()) {
            // 거래처×만기일 단위로 한 번만 — 매일 돌아도 중복 적재되지 않는다.
            String dedupKey = TYPE + ":" + row.partnerId() + ":" + row.assureExpiry();
            if (notificationRepository.findByDedupKey(dedupKey).isPresent()) {
                continue;
            }
            boolean expired = "EXPIRED".equals(row.status());
            String title = expired
                    ? "담보 만기 경과: " + row.name()
                    : "담보 만기 임박: " + row.name();
            String message = String.format("만기일 %s (%s일 %s) · 담보금액 %s",
                    row.assureExpiry(),
                    Math.abs(row.daysUntilExpiry()),
                    expired ? "경과" : "남음",
                    row.assureAmount() == null ? "-" : String.format("%,d원", row.assureAmount()));
            notificationRepository.save(Notification.of(
                    TYPE, dedupKey, title, message, "PARTNER", row.partnerId(), baseDate));
            created++;
        }
        return created;
    }
}
