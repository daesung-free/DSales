package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.BomItem;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {

    /** 구성품(child)을 즉시 로드(JOIN FETCH) — 재고 갱신 중 세션 clear에도 detach 안전. */
    @Query("select b from BomItem b join fetch b.child where b.parent.id = :parentId")
    List<BomItem> findByParentId(@Param("parentId") Long parentId);

    /**
     * 완제품의 기존 BOM 구성을 논리삭제(게이트규칙: 물리 DELETE 금지).
     *
     * <p>벌크 UPDATE라 영속성 컨텍스트를 우회하므로 flush/clear를 함께 건다. 재삽입 전에 삭제가
     * DB에 반영돼야 유니크 (parent, child, del_key) 충돌을 피할 수 있다.
     * {@code @SQLRestriction}은 벌크 연산에 적용되지 않으므로 미삭제 조건을 명시한다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update BomItem b set b.deletedAt = :now, b.deletedBy = :actor "
            + "where b.parent.id = :parentId and b.deletedAt is null")
    int softDeleteByParentId(@Param("parentId") Long parentId,
                             @Param("now") LocalDateTime now,
                             @Param("actor") String actor);
}
