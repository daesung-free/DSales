package com.daesung.sales.common.sequence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SequenceRepository extends JpaRepository<SeqRegistry, String> {

    /** 순번 +1 (행잠금). 반환=영향 행수. */
    @Modifying
    @Query(value = "UPDATE seq_registry SET seq_val = seq_val + 1 WHERE seq_name = :name", nativeQuery = true)
    int bump(@Param("name") String name);

    /** 현재 순번. bump 직후 같은 트랜잭션에서 호출. */
    @Query(value = "SELECT seq_val FROM seq_registry WHERE seq_name = :name", nativeQuery = true)
    Long current(@Param("name") String name);
}
