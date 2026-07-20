package com.daesung.sales.salestype.repository;

import com.daesung.sales.salestype.entity.OutType;
import com.daesung.sales.salestype.entity.OutTypeMapping;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OutTypeMappingRepository extends JpaRepository<OutTypeMapping, OutType> {
}
