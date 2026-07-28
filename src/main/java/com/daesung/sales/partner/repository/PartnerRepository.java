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
}
