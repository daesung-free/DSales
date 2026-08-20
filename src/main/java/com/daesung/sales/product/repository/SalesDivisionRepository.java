package com.daesung.sales.product.repository;

import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.SalesDivision;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/** 세부구분 마스터 조회. 목록은 항상 대분류 → 정렬순서 → 코드 순으로 준다(화면 순서 고정). */
public interface SalesDivisionRepository extends JpaRepository<SalesDivision, Long> {

    Optional<SalesDivision> findByCode(String code);

    List<SalesDivision> findAllByOrderByMajorCategoryAscSortOrderAscCodeAsc();

    List<SalesDivision> findByMajorCategoryOrderBySortOrderAscCodeAsc(MajorCategory majorCategory);

    List<SalesDivision> findByUseYnTrueOrderByMajorCategoryAscSortOrderAscCodeAsc();

    boolean existsByCode(String code);
}
