package com.daesung.sales.permission.repository;

import com.daesung.sales.permission.entity.MenuScreen;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface MenuScreenRepository extends JpaRepository<MenuScreen, Long> {

    /** 매칭 순서대로. 구체적인 패턴이 앞에 온다(‼️순서를 어기면 엉뚱한 화면 권한을 따라간다). */
    List<MenuScreen> findAllByOrderBySortOrderAscIdAsc();

    Optional<MenuScreen> findByCode(String code);
}
