package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.ProductLogisRate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductLogisRateRepository extends JpaRepository<ProductLogisRate, Long> {

    Optional<ProductLogisRate> findByProductId(Long productId);

    boolean existsByProductId(Long productId);

    /** 작업구분별 대상 — 일괄반영이 훑는다. */
    List<ProductLogisRate> findByPackType(int packType);

    /**
     * 목록. 상품을 fetch join 해 N+1을 막는다. 키워드는 상품코드·상품명을 함께 훑는다.
     *
     * <p>정렬은 상품코드다 — 등록순으로 두면 같은 분류의 상품이 목록 여기저기 흩어져
     * 담당자가 단가를 비교할 수 없다.
     */
    @Query("""
            select r from ProductLogisRate r join fetch r.product p
             where (:packType is null or r.packType = :packType)
               and (:kw is null or lower(p.code) like :kw or lower(p.name) like :kw)
             order by p.code asc
            """)
    List<ProductLogisRate> search(@Param("packType") Integer packType, @Param("kw") String kw);
}
