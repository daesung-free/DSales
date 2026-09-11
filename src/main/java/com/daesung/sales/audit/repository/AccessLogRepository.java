package com.daesung.sales.audit.repository;

import com.daesung.sales.audit.entity.AccessAction;
import com.daesung.sales.audit.entity.AccessLog;
import java.time.LocalDateTime;
import java.util.Collection;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessLogRepository extends JpaRepository<AccessLog, Long> {

    /**
     * 행위 조회. 축마다 {@code anyXxx} 플래그를 짝으로 둔다 —
     * 빈 컬렉션을 {@code in ()} 으로 넘기면 DB마다 다르게 깨진다(MultiSelect 규약과 같다).
     */
    @Query("""
            select a from AccessLog a
            where (:from is null or a.createdAt >= :from)
              and (:to is null or a.createdAt <= :to)
              and (:username is null or lower(a.username) like :username)
              and (:anyAction = true or a.action in :actions)
              and (:successOnly = false or a.success = true)
            order by a.createdAt desc, a.id desc
            """)
    Page<AccessLog> search(@Param("from") LocalDateTime from,
                           @Param("to") LocalDateTime to,
                           @Param("username") String username,
                           @Param("anyAction") boolean anyAction,
                           @Param("actions") Collection<AccessAction> actions,
                           @Param("successOnly") boolean successOnly,
                           Pageable pageable);

    /** 보존기간 경과분 삭제. 배치에서만 호출한다 — 그 외 경로로는 지우지 않는다. */
    @Modifying
    @Query("delete from AccessLog a where a.createdAt < :before")
    int deleteOlderThan(@Param("before") LocalDateTime before);
}
