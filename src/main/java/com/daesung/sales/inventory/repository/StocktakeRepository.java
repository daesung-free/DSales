package com.daesung.sales.inventory.repository;

import com.daesung.sales.inventory.entity.Stocktake;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StocktakeRepository extends JpaRepository<Stocktake, Long> {
}
