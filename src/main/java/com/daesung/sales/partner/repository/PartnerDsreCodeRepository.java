package com.daesung.sales.partner.repository;

import com.daesung.sales.partner.entity.PartnerDsreCode;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

/** 거래처 ↔ DSRE 거래처코드 매핑(1:N). */
public interface PartnerDsreCodeRepository
        extends JpaRepository<PartnerDsreCode, PartnerDsreCode.Key> {

    /** 학교가 들고 있는 DSRE 코드들로 거래처를 찾는다. */
    List<PartnerDsreCode> findByDsreCodeIn(Collection<String> dsreCodes);
}
