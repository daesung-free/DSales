package com.daesung.sales.closing.repository;

import com.daesung.sales.closing.entity.PeriodLock;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PeriodLockRepository extends JpaRepository<PeriodLock, Long> {

    Optional<PeriodLock> findByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    List<PeriodLock> findByPeriodYearOrderByPeriodMonth(int periodYear);

    /** 해당 월이 마감(잠금)되었는지. */
    @Query("select case when count(p) > 0 then true else false end from PeriodLock p "
            + "where p.periodYear = :year and p.periodMonth = :month and p.locked = true")
    boolean isLocked(@Param("year") int year, @Param("month") int month);
}
