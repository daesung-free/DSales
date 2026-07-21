package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.BomItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {

    /** 구성품(child)을 즉시 로드(JOIN FETCH) — 재고 갱신 중 세션 clear에도 detach 안전. */
    @Query("select b from BomItem b join fetch b.child where b.parent.id = :parentId")
    List<BomItem> findByParentId(@Param("parentId") Long parentId);

    void deleteByParentId(Long parentId);
}
