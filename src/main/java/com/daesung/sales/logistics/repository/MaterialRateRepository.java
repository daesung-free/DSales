package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.MaterialRate;
import com.daesung.sales.product.entity.MaterialType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MaterialRateRepository extends JpaRepository<MaterialRate, Long> {

    Optional<MaterialRate> findByMaterialTypeAndPackType(MaterialType materialType, int packType);

    List<MaterialRate> findAllByOrderByMaterialTypeAscPackTypeAsc();
}
