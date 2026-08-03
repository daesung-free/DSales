package com.daesung.sales.batch.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배치 실행 이력. 근거: 개발문서 19.0 완료조건 "배치 정상완료 + 실패 재시도 로직 검증".
 * 성공/실패와 시도 횟수를 남겨야 검수 때 "돌았다"는 증거를 제시할 수 있다.
 */
@Entity
@Table(name = "batch_job_run")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BatchJobRun extends BaseEntity {

    public static final String SUCCESS = "SUCCESS";
    public static final String FAILED = "FAILED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "job_name", nullable = false, length = 50)
    private String jobName;

    /** 기준일자 — 같은 날 중복 실행 판정에 쓴다. */
    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;

    @Column(nullable = false, length = 10)
    private String status;

    /** 시도 횟수(1부터). 재시도하면 증가한 값으로 별도 행이 쌓인다. */
    @Column(nullable = false)
    private int attempt;

    /** 처리 건수(생성된 알림 수 등). */
    @Column(nullable = false)
    private int affected;

    @Column(length = 500)
    private String message;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "finished_at")
    private LocalDateTime finishedAt;

    public static BatchJobRun start(String jobName, LocalDate runDate, int attempt) {
        BatchJobRun r = new BatchJobRun();
        r.jobName = jobName;
        r.runDate = runDate;
        r.attempt = attempt;
        r.status = FAILED;   // 완료 시 성공으로 전환 — 중간에 죽으면 실패로 남는 게 맞다
        r.startedAt = LocalDateTime.now();
        return r;
    }

    public void succeed(int affected) {
        this.status = SUCCESS;
        this.affected = affected;
        this.finishedAt = LocalDateTime.now();
    }

    public void fail(String message) {
        this.status = FAILED;
        this.message = (message == null) ? null : message.substring(0, Math.min(500, message.length()));
        this.finishedAt = LocalDateTime.now();
    }

    public boolean isSuccess() {
        return SUCCESS.equals(status);
    }
}
