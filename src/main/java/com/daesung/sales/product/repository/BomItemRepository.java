package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.BomItem;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BomItemRepository extends JpaRepository<BomItem, Long> {
    List<BomItem> findByParentId(Long parentId);
}
