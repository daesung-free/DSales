package com.daesung.sales.school.repository;

import com.daesung.sales.school.entity.School;
import com.daesung.sales.school.entity.SchoolSource;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SchoolRepository extends JpaRepository<School, Long> {
    Optional<School> findBySchoolCode(String schoolCode);

    /**
     * 학교/학원검색(29p). 근거: 레거시 {@code 학교검색.vb} 조회 SQL을 그대로 옮겼다.
     *
     * <p>★<b>특약점명은 세 축을 다 뒤진다</b> — 레거시 원문:
     * {@code (custName like ? or mCustName like ? or iCustName like ?)}.
     * 같은 학교라도 상품군에 따라 담당 특약점이 달라, 대표 특약점만 보면 못 찾는다.
     *
     * <p>필터 4종(학교코드·학교명·지역·특약점명)은 전부 부분일치다(레거시 {@code like '%..%'}).
     * 빈 값은 조건에서 빠진다 — 레거시는 빈 문자열을 넣어 {@code like '%%'}가 되게 했는데,
     * 그러면 NULL인 행이 통째로 빠진다(SQL에서 NULL like '%%'는 참이 아니다).
     */
    @Query("""
            select s from School s
             where (:schoolCode is null or s.schoolCode like %:schoolCode%)
               and (:schoolName is null or s.schoolName like %:schoolName%)
               and (:region is null or s.cityCode like %:region% or s.city like %:region%
                    or s.region like %:region%)
               and (:partnerName is null
                    or s.custName like %:partnerName%
                    or s.mockPartnerName like %:partnerName%
                    or s.icPartnerName like %:partnerName%)
             order by s.schoolCode
            """)
    List<School> search(@Param("schoolCode") String schoolCode,
                        @Param("schoolName") String schoolName,
                        @Param("region") String region,
                        @Param("partnerName") String partnerName);

    /** 동기화 매칭키 조회 — (거래처코드, 학교코드) 복합. */
    Optional<School> findByCustCodeAndSchoolCode(String custCode, String schoolCode);

    /** 출처별 조회. 동기화는 DSRE 행만 대상으로 하고 MANUAL 행은 손대지 않는다. */
    java.util.List<School> findBySource(SchoolSource source);

    Page<School> findBySchoolCodeContainingIgnoreCaseOrSchoolNameContainingIgnoreCase(
            String schoolCode, String schoolName, Pageable pageable);
}
