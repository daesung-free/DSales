package com.daesung.sales.school.repository;

import com.daesung.sales.school.entity.School;
import com.daesung.sales.school.entity.SchoolSource;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findBySchoolCode(String schoolCode);

    /** 동기화 매칭키 조회 — (거래처코드, 학교코드) 복합. */
    Optional<School> findByCustCodeAndSchoolCode(String custCode, String schoolCode);

    /** 출처별 조회. 동기화는 DSRE 행만 대상으로 하고 MANUAL 행은 손대지 않는다. */
    java.util.List<School> findBySource(SchoolSource source);

    Page<School> findBySchoolCodeContainingIgnoreCaseOrSchoolNameContainingIgnoreCase(
            String schoolCode, String schoolName, Pageable pageable);
}
