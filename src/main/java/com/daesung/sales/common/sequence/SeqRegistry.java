package com.daesung.sales.common.sequence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 채번 시퀀스 레지스트리(MySQL 8 시퀀스 부재 대체). seq_name별 순번을 행잠금으로 원자 증가.
 * 근거: PostgreSQL 시퀀스 → MySQL 이식. 접두어/날짜 포맷은 서비스 계층(I/P/OUT/수금/실사).
 */
@Entity
@Table(name = "seq_registry")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SeqRegistry {

    @Id
    @Column(name = "seq_name", length = 64)
    private String seqName;

    @Column(name = "seq_val", nullable = false)
    private long seqVal;
}
