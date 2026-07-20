package com.daesung.sales.salestype.repository;

import com.daesung.sales.salestype.entity.OutTypeMapping;
import com.daesung.sales.salestype.entity.ShipmentType;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutTypeMappingRepository extends JpaRepository<OutTypeMapping, ShipmentType> {
}
