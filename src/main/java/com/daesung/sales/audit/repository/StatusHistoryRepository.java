package com.daesung.sales.audit.repository;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.entity.StatusHistory;
import java.time.LocalDateTime;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StatusHistoryRepository extends JpaRepository<StatusHistory, Long> {

    /**
     * 감사 조회. 대상(종류·id) / 변경자 / 기간을 조합해 좁힌다. 미지정 조건은 무시한다.
     * 최신순 — "가장 최근에 무슨 일이 있었나"가 먼저 궁금하다.
     */
    @Query("""
            select h from StatusHistory h
             where (:entityType is null or h.entityType = :entityType)
               and (:entityId is null or h.entityId = :entityId)
               and (:changedBy is null or h.changedBy = :changedBy)
               and (:from is null or h.changedAt >= :from)
               and (:to is null or h.changedAt <= :to)
             order by h.changedAt desc, h.id desc
            """)
    Page<StatusHistory> search(@Param("entityType") StatusEntityType entityType,
                               @Param("entityId") Long entityId,
                               @Param("changedBy") String changedBy,
                               @Param("from") LocalDateTime from,
                               @Param("to") LocalDateTime to,
                               Pageable pageable);
}
