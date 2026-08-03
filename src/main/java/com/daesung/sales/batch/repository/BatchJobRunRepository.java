package com.daesung.sales.batch.repository;

import com.daesung.sales.batch.entity.BatchJobRun;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BatchJobRunRepository extends JpaRepository<BatchJobRun, Long> {

    List<BatchJobRun> findByJobNameAndRunDateOrderByAttemptAsc(String jobName, LocalDate runDate);

    Page<BatchJobRun> findByJobNameOrderByIdDesc(String jobName, Pageable pageable);

    Page<BatchJobRun> findAllByOrderByIdDesc(Pageable pageable);
}
