package com.daesung.sales.permission.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 화면 마스터 — 요청 경로를 화면에 붙이는 표.
 *
 * <p>권한이 "화면 단위"로 정해지는데(발주처 2단계), 들어오는 것은 HTTP 요청이다.
 * 그 둘을 잇는 것이 {@link #pathPattern}이다.
 *
 * <p>★{@link #sortOrder}가 중요하다. 패턴이 겹칠 때 <b>먼저 걸리는 것이 이긴다</b> —
 * {@code /stock/ledger/**}가 {@code /stock/**}보다 앞에 있어야 제품수불부가
 * 입고등록 권한을 따라가지 않는다.
 */
@Entity
@Table(name = "menu_screen")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MenuScreen {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 40)
    private String code;

    @Column(nullable = false, length = 60)
    private String name;

    /** 메뉴그룹(ORDER/SALES/CLOSING/LOGISTICS/MASTER) — 화면 목록을 묶어 보여주는 축. */
    @Column(name = "menu_group", nullable = false, length = 20)
    private String menuGroup;

    /** Ant 경로 패턴(/api/v1 이후). 이 화면에 속하는 요청을 가려낸다. */
    @Column(name = "path_pattern", nullable = false, length = 200)
    private String pathPattern;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
