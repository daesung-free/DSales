package com.daesung.sales.audit.repository;

import com.daesung.sales.audit.entity.MasterChangeLog;
import com.daesung.sales.audit.entity.MasterEntityType;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MasterChangeLogRepository extends JpaRepository<MasterChangeLog, Long> {

    /** 대상·변경자·기간 필터 조회. 전부 선택값이라 널 허용 조건으로 묶는다. */
    @Query("""
            select l from MasterChangeLog l
             where (:entityType is null or l.entityType = :entityType)
               and (:entityId   is null or l.entityId   = :entityId)
               and (:changedBy  is null or l.changedBy  = :changedBy)
               and (:from is null or l.changedAt >= :from)
               and (:to   is null or l.changedAt <  :to)
             order by l.changedAt desc, l.id desc
            """)
    Page<MasterChangeLog> search(@Param("entityType") MasterEntityType entityType,
                                 @Param("entityId") Long entityId,
                                 @Param("changedBy") String changedBy,
                                 @Param("from") LocalDateTime from,
                                 @Param("to") LocalDateTime to,
                                 Pageable pageable);
}
