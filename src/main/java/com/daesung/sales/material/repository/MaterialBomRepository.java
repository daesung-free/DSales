package com.daesung.sales.material.repository;

import com.daesung.sales.material.entity.MaterialBom;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MaterialBomRepository extends JpaRepository<MaterialBom, Long> {

    /** 세트의 전체 매칭(공통 + 회차별). 자재·회차 즉시 로드 — 목록이 매 행 이름을 쓴다. */
    @Query("""
            select b from MaterialBom b
              join fetch b.material
              left join fetch b.roundProduct
             where b.setProduct.id = :setProductId
             order by b.roundProduct.code nulls first, b.material.code
            """)
    List<MaterialBom> findBySet(@Param("setProductId") Long setProductId);

    /** 같은 조합이 이미 있는지. 공통(회차 null)은 DB UNIQUE가 못 막아 여기서 본다. */
    @Query("""
            select b from MaterialBom b
             where b.setProduct.id = :setProductId
               and b.material.id = :materialId
               and ((:roundProductId is null and b.roundProduct is null)
                    or b.roundProduct.id = :roundProductId)
            """)
    Optional<MaterialBom> findMatch(@Param("setProductId") Long setProductId,
                                    @Param("roundProductId") Long roundProductId,
                                    @Param("materialId") Long materialId);

    /** 자재가 어딘가에 매칭돼 있는지(삭제 차단용). */
    long countByMaterialId(Long materialId);
}
