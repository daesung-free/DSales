package com.daesung.sales.batch.service;

import com.daesung.sales.audit.service.AccessLogService;
import com.daesung.sales.batch.entity.BatchJobRun;
import java.time.LocalDate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * 접근로그 보존기간 정리.
 *
 * <p>★기록을 <b>안 지우면 못 쓰게 된다.</b> 하루 수천 건이면 1년에 수백만 행이고,
 * 그때부터는 조회가 느려져 정작 필요할 때 못 본다. 보존기간을 정해 두는 것이
 * 기록을 살려 두는 방법이다.
 *
 * <p>★<b>지우는 경로는 여기 하나뿐</b>이다. 엔티티에 수정·삭제 메서드를 두지 않았고
 * 레포지토리의 삭제도 이 배치만 호출한다 — 고칠 수 있는 기록은 증거가 되지 못한다.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccessLogPurgeBatch {

    public static final String JOB_NAME = "ACCESS_LOG_PURGE";

    private static final int MAX_ATTEMPTS = 2;

    private final AccessLogService accessLogService;
    private final BatchJobRunner runner;

    /** 보존 개월 수. 발주처 협의로 바뀔 수 있어 설정으로 둔다(기본 12개월). */
    @Value("${daesung.audit.access-log-retention-months:12}")
    private int retentionMonths;

    public BatchJobRun run(LocalDate runDate) {
        return runner.run(JOB_NAME, runDate, MAX_ATTEMPTS, () -> {
            LocalDate cutoff = runDate.minusMonths(Math.max(retentionMonths, 1));
            int deleted = accessLogService.purgeOlderThan(cutoff.atStartOfDay());
            log.info("접근로그 정리: {} 이전 {}건 삭제(보존 {}개월)", cutoff, deleted, retentionMonths);
            return deleted;
        });
    }
}
