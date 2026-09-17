package com.daesung.sales.closing.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 월마감 잠금(period_locks). 근거: 요구사항정의서 3대 로직/DB-30(완전 신규).
 * locked=true인 (year,month)의 재무 쓰기를 차단. (year,month) 유일.
 */
@Entity
@Table(name = "period_lock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PeriodLock extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(nullable = false)
    private boolean locked = false;

    @Column(name = "locked_at")
    private LocalDateTime lockedAt;

    @Column(name = "locked_by", length = 50)
    private String lockedBy;

    @Column(length = 500)
    private String memo;

    /**
     * 마감 해제 사유. ★확정 사유(memo)와 <b>따로</b> 둔다.
     *
     * <p>한 칸을 확정·해제가 번갈아 쓰면 마지막에 쓴 쪽만 남는다 —
     * 해제했다가 다시 마감하면 "왜 열었는지"가 사라진다.
     * 감사에서 더 중요한 건 확정이 아니라 <b>해제</b> 쪽이다.
     */
    @Column(name = "unlock_memo", length = 500)
    private String unlockMemo;

    @Column(name = "unlocked_by", length = 50)
    private String unlockedBy;

    @Column(name = "unlocked_at")
    private LocalDateTime unlockedAt;

    public static PeriodLock create(int periodYear, int periodMonth) {
        PeriodLock p = new PeriodLock();
        p.periodYear = periodYear;
        p.periodMonth = periodMonth;
        p.locked = false;
        return p;
    }

    /** 마감(잠금). */
    public void lock(String lockedBy, String memo) {
        this.locked = true;
        this.lockedAt = LocalDateTime.now();
        this.lockedBy = lockedBy;
        this.memo = memo;
    }

    /**
     * 마감 해제(재오픈).
     *
     * <p>‼️다시 마감해도 해제 기록은 지우지 않는다 — "이 달은 한 번 열렸었다"는 사실 자체가
     * 감사 대상이다. 덮으면 그게 사라진다.
     */
    public void unlock(String unlockedBy, String unlockMemo) {
        this.locked = false;
        this.lockedAt = null;
        this.lockedBy = null;
        this.unlockedBy = unlockedBy;
        this.unlockedAt = LocalDateTime.now();
        this.unlockMemo = unlockMemo;
    }
}
