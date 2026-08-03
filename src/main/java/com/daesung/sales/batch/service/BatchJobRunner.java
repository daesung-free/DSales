package com.daesung.sales.batch.service;

import com.daesung.sales.batch.entity.BatchJobRun;
import com.daesung.sales.batch.repository.BatchJobRunRepository;
import java.time.LocalDate;
import java.util.function.IntSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 배치 공통 실행 래퍼 — 이력 기록 + 실패 재시도.
 * 근거: 개발문서 19.0 완료조건 "배치 정상완료 + 실패 재시도 로직 검증".
 *
 * <p>이력은 <b>본 작업과 별도 트랜잭션</b>(REQUIRES_NEW)으로 남긴다.
 * 같은 트랜잭션이면 작업이 실패해 롤백될 때 "실패했다"는 기록까지 함께 사라져,
 * 정작 검수 때 봐야 할 실패 이력이 남지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BatchJobRunner {

    private final BatchJobRunRepository runRepository;

    /**
     * 작업을 최대 {@code maxAttempts}회까지 시도하고 매 시도를 이력으로 남긴다.
     *
     * @param work 실제 작업. 반환값 = 처리 건수
     * @return 마지막 시도의 이력
     */
    public BatchJobRun run(String jobName, LocalDate runDate, int maxAttempts, IntSupplier work) {
        BatchJobRun last = null;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            BatchJobRun run = save(BatchJobRun.start(jobName, runDate, attempt));
            try {
                int affected = work.getAsInt();
                run.succeed(affected);
                last = save(run);
                log.info("배치 성공 job={} date={} attempt={} affected={}", jobName, runDate, attempt, affected);
                return last;
            } catch (RuntimeException e) {
                run.fail(e.getMessage());
                last = save(run);
                log.warn("배치 실패 job={} date={} attempt={}/{} : {}",
                        jobName, runDate, attempt, maxAttempts, e.getMessage());
            }
        }
        return last;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public BatchJobRun save(BatchJobRun run) {
        return runRepository.save(run);
    }
}
