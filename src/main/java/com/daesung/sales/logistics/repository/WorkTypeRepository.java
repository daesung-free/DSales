package com.daesung.sales.logistics.repository;

import com.daesung.sales.logistics.entity.WorkType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WorkTypeRepository extends JpaRepository<WorkType, Long> {

    Optional<WorkType> findByPackType(int packType);

    boolean existsByPackType(int packType);

    List<WorkType> findAllByOrderBySortOrderAscPackTypeAsc();
}
