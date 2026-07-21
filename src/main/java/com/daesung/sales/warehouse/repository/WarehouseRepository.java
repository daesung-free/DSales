package com.daesung.sales.warehouse.repository;

import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.entity.WarehouseType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WarehouseRepository extends JpaRepository<Warehouse, Long> {
    Optional<Warehouse> findByCode(String code);
    List<Warehouse> findByType(WarehouseType type);

    Page<Warehouse> findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
            String code, String name, Pageable pageable);
}
