package com.daesung.sales.batch.dto;

import com.daesung.sales.batch.entity.BatchJobRun;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** 배치 실행 결과·이력. */
public record BatchRunResponse(
        @Schema(description = "이력 id") Long id,
        @Schema(description = "잡 이름") String jobName,
        @Schema(description = "기준일자") LocalDate runDate,
        @Schema(description = "상태 SUCCESS/FAILED") String status,
        @Schema(description = "시도 횟수") int attempt,
        @Schema(description = "처리 건수") int affected,
        @Schema(description = "실패 사유") String message,
        @Schema(description = "시작 시각") LocalDateTime startedAt,
        @Schema(description = "종료 시각") LocalDateTime finishedAt
) {
    public static BatchRunResponse from(BatchJobRun r) {
        return new BatchRunResponse(r.getId(), r.getJobName(), r.getRunDate(), r.getStatus(),
                r.getAttempt(), r.getAffected(), r.getMessage(), r.getStartedAt(), r.getFinishedAt());
    }
}
