package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductRepository extends JpaRepository<Product, Long> {
    Optional<Product> findByCode(String code);

    Page<Product> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Pageable pageable);

    /** 이 세부구분을 쓰는 상품 수. 세부구분을 지워도 되는지 판단한다(쓰는 곳이 있으면 비활성만). */
    long countBySalesDivision(String salesDivision);

    /** 분류코드에 속한 도서 id 전체. 분류 단위 플래그 일괄 적용(수불부/단가 노출)에 쓴다. */
    @org.springframework.data.jpa.repository.Query("select p.id from Product p where p.catCode = :catCode")
    java.util.List<Long> findIdsByCatCode(@org.springframework.data.repository.query.Param("catCode") String catCode);
}
