package com.daesung.sales.logistics.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 개별 수정된 물류단가 행(예외). 작업구분 일괄 반영이 이 행을 건너뛴다.
 * 근거: 36p 수정요청안 — "예외 처리된 항목에는 영향을 주지 않아야 하며,
 * 예외 항목은 빨간색 등으로 구분 표시되어야 함".
 *
 * <p>★"기준단가와 값이 다르면 예외"로 판정하지 않는 이유:
 * 한 번도 일괄적용을 받지 않은 새 행까지 예외가 되어 영영 일괄적용에서 빠진다.
 * <b>담당자가 행을 직접 고친 사실</b>을 기록해야 의도와 우연이 갈린다.
 */
@Entity
@Table(name = "logis_rate_override")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogisRateOverride {

    /** DSRE2 시행코드. 단가 행과 1:1이라 그대로 PK로 쓴다. */
    @Id
    @Column(name = "dtl_cd")
    private Integer dtlCd;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    /** 누가 예외로 만들었는지 — 나중에 "왜 이 행만 안 바뀌냐"는 물음에 답이 된다. */
    @Column(name = "created_by", length = 50)
    private String createdBy;

    public static LogisRateOverride of(int dtlCd, String actor) {
        LogisRateOverride o = new LogisRateOverride();
        o.dtlCd = dtlCd;
        o.createdAt = LocalDateTime.now();
        o.createdBy = actor;
        return o;
    }
}
