package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.Stocktake;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {

    /** 실사번호(ST) 채번용 시퀀스. */
    @Query(value = "SELECT nextval('seq_stocktake_no')", nativeQuery = true)
    long nextStocktakeSeq();
}
