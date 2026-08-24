package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.LogisRateOverride;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LogisRateOverrideRepository extends JpaRepository<LogisRateOverride, Integer> {

    /** 예외로 등록된 시행코드 전부. 일괄 반영이 이 목록을 건너뛴다. */
    @Override
    List<LogisRateOverride> findAll();
}
