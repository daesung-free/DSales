package com.daesung.sales.permission.service;

import com.daesung.sales.auth.entity.Role;
import com.daesung.sales.permission.entity.MenuScreen;
import com.daesung.sales.permission.entity.RoleScreenPermission;
import com.daesung.sales.permission.entity.ScreenPermission;
import com.daesung.sales.permission.repository.MenuScreenRepository;
import com.daesung.sales.permission.repository.RoleScreenPermissionRepository;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.AntPathMatcher;

/**
 * 요청 → 화면 → 권한 판정. 예전엔 이 판정이 {@code SecurityConfig} 코드에 박혀 있었고,
 * 지금은 <b>표에서 읽는다</b> — 발주처 요구가 "관리자가 운영 중 자유롭게 조정"이기 때문이다.
 *
 * <p>★판정은 <b>2단계(화면 권한) 하나만</b> 본다. 1단계(역할×메뉴그룹)는 초기값을 만드는
 * 규칙이라 시딩에만 쓴다 — 둘 다 보면 "그룹은 ◐인데 화면은 Y"일 때 어느 쪽이 이기는지
 * 규칙이 하나 더 생기고, 그 규칙은 아무 데도 적혀 있지 않다.
 *
 * <p>★캐시를 둔다. 매 요청마다 두 표를 읽으면 모든 API가 느려진다.
 * 권한을 바꾸면 {@link #reload()}로 비운다(자주 바뀌는 데이터가 아니다).
 */
@Service
@RequiredArgsConstructor
public class PermissionResolver {

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final MenuScreenRepository screenRepository;
    private final RoleScreenPermissionRepository permissionRepository;

    /** 화면 목록(매칭 순서) — 통째로 갈아 끼운다. */
    private volatile List<MenuScreen> screens;

    /** (역할, 화면id) → 권한. */
    private volatile Map<String, ScreenPermission> matrix;

    /**
     * 이 요청이 허용되는가.
     *
     * @param path   {@code /api/v1} 를 뗀 경로
     * @param write  쓰기 요청인가(POST/PUT/DELETE/PATCH)
     */
    @Transactional(readOnly = true)
    public boolean allows(Role role, String path, boolean write) {
        MenuScreen screen = screenOf(path);
        if (screen == null) {
            // 어느 화면에도 안 붙은 경로. 조회는 인증만으로 허용하고 쓰기는 막는다 —
            // 규칙 없이 추가된 쓰기 API가 조용히 열리면 안 된다(기존 deny-by-default와 같은 취지).
            return !write;
        }
        ScreenPermission p = permissionOf(role, screen.getId());
        return p.allows(write);
    }

    /** 이 경로가 속한 화면. 없으면 null. 순서상 <b>먼저 걸리는 패턴</b>이 이긴다. */
    @Transactional(readOnly = true)
    public MenuScreen screenOf(String path) {
        for (MenuScreen s : screens()) {
            if (MATCHER.match(s.getPathPattern(), path)) {
                return s;
            }
        }
        return null;
    }

    /** 역할×화면 권한. 표에 없으면 NONE(모르는 조합을 열어 두지 않는다). */
    @Transactional(readOnly = true)
    public ScreenPermission permissionOf(Role role, Long screenId) {
        return matrix().getOrDefault(role.name() + "#" + screenId, ScreenPermission.NONE);
    }

    /** 권한을 바꾼 뒤 호출 — 다음 요청부터 새 값으로 판정한다. */
    public void reload() {
        screens = null;
        matrix = null;
    }

    private List<MenuScreen> screens() {
        List<MenuScreen> s = screens;
        if (s == null) {
            s = screenRepository.findAllByOrderBySortOrderAscIdAsc();
            screens = s;
        }
        return s;
    }

    private Map<String, ScreenPermission> matrix() {
        Map<String, ScreenPermission> m = matrix;
        if (m == null) {
            m = new HashMap<>();
            for (RoleScreenPermission p : permissionRepository.findAllWithScreen()) {
                m.put(p.getRole().name() + "#" + p.getScreen().getId(), p.getPermission());
            }
            matrix = m;
        }
        return m;
    }
}
