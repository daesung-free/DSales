package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.PartnerSupplyRate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 거래처별 대분류 공급률(34p) 조회. */
public interface PartnerSupplyRateRepository extends JpaRepository<PartnerSupplyRate, Long> {

    /** 매출등록 공급률 자동조회 — 거래처 × 상품 대분류. 사용여부가 꺼진 매핑은 없는 것으로 본다. */
    @Query("select m from PartnerSupplyRate m "
            + "where m.partner.id = :partnerId and m.majorCategory = :majorCategory and m.useYn = true")
    Optional<PartnerSupplyRate> findActive(@Param("partnerId") Long partnerId,
                                           @Param("majorCategory") MajorCategory majorCategory);

    /** 관리 화면용 단건(사용여부 무관) — 꺼둔 매핑도 고칠 수 있어야 한다. */
    Optional<PartnerSupplyRate> findByPartnerIdAndMajorCategory(Long partnerId, MajorCategory majorCategory);

    /**
     * 34p 목록. 거래처구분·거래처·대분류로 좁힌다(정본: "거래처구분 필터로 좁힌 뒤 여러 거래처를 체크").
     * 거래처를 fetch join 해 목록에서 코드·명·구분을 N+1 없이 함께 보여준다.
     */
    @Query("select m from PartnerSupplyRate m join fetch m.partner p "
            + "where (:partnerId is null or p.id = :partnerId) "
            + "and (:majorCategory is null or m.majorCategory = :majorCategory) "
            + "and (:clientCategory is null or p.clientCategory = :clientCategory) "
            + "order by p.code asc, m.majorCategory asc")
    List<PartnerSupplyRate> search(@Param("partnerId") Long partnerId,
                                   @Param("majorCategory") MajorCategory majorCategory,
                                   @Param("clientCategory") String clientCategory);
}
