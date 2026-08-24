package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.LogisCostSnapshot;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogisCostSnapshotRepository extends JpaRepository<LogisCostSnapshot, Long> {

    List<LogisCostSnapshot> findByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    boolean existsByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);

    /** 마감 해제 시 지운다 — 다시 마감하면 그 시점 단가로 새로 굳는다. */
    void deleteByPeriodYearAndPeriodMonth(int periodYear, int periodMonth);
}
