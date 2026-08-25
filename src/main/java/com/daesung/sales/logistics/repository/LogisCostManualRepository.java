package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.LogisCostManual;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogisCostManualRepository extends JpaRepository<LogisCostManual, Long> {

    /** 기간 내 수기 행(접수일자 기준). 자동계산분과 같은 축이라 그대로 합쳐진다. */
    List<LogisCostManual> findByReqDateBetween(LocalDate from, LocalDate to);
}
