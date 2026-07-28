package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.ProductPartnerPrice;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductPartnerPriceRepository extends JpaRepository<ProductPartnerPrice, Long> {

    /** 특정 도서의 거래처별 매핑 전체(거래처 즉시 로드). */
    @Query("select m from ProductPartnerPrice m join fetch m.partner where m.product.id = :productId order by m.partner.code")
    List<ProductPartnerPrice> findByProductId(Long productId);

    /** 도서×거래처 단건(매출등록 자동조회용). */
    @Query("select m from ProductPartnerPrice m join fetch m.partner "
            + "where m.product.id = :productId and m.partner.id = :partnerId")
    Optional<ProductPartnerPrice> findByProductIdAndPartnerId(Long productId, Long partnerId);
}
