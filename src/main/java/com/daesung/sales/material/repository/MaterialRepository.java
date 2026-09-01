package com.daesung.sales.material.repository;

import com.daesung.sales.material.entity.Material;
import com.daesung.sales.product.entity.MaterialType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialRepository extends JpaRepository<Material, Long> {

    Optional<Material> findByCode(String code);

    boolean existsByCode(String code);

    /** 목록. 자재구분·사용여부·검색어(코드/자재명)로 좁힌다. */
    @Query("""
            select m from Material m
             where (:type is null or m.materialType = :type)
               and (:useYn is null or m.useYn = :useYn)
               and (:keyword is null or m.code like %:keyword% or m.name like %:keyword%)
             order by m.materialType, m.code
            """)
    List<Material> search(@Param("type") MaterialType type,
                          @Param("useYn") Boolean useYn,
                          @Param("keyword") String keyword);
}
