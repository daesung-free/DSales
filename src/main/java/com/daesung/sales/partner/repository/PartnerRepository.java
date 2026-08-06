package com.daesung.sales.partner.repository;

import com.daesung.sales.partner.entity.Partner;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PartnerRepository extends JpaRepository<Partner, Long> {
    Optional<Partner> findByCode(String code);

    Page<Partner> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Pageable pageable);

    /** 담보 만기일이 기준일(threshold) 이하인 거래처(만료+임박). 만기일 오름차순. null 만기일은 자동 제외. */
    List<Partner> findByAssureExpiryLessThanEqualOrderByAssureExpiryAsc(LocalDate threshold);

    /**
     * 거래처 검색. 키워드(코드·거래처명 부분일치) + 만료 포함 여부.
     * includeExpired=false면 거래중(end_date IS NULL)만 — 레거시 거래처관리 기본 동작.
     */
    @org.springframework.data.jpa.repository.Query("""
            select p from Partner p
             where (:keyword is null
                    or lower(p.code) like lower(concat('%', :keyword, '%'))
                    or lower(p.name) like lower(concat('%', :keyword, '%')))
               and (:includeExpired = true or p.endDate is null)
             order by p.code
            """)
    org.springframework.data.domain.Page<Partner> search(
            @org.springframework.data.repository.query.Param("keyword") String keyword,
            @org.springframework.data.repository.query.Param("includeExpired") boolean includeExpired,
            org.springframework.data.domain.Pageable pageable);
}
