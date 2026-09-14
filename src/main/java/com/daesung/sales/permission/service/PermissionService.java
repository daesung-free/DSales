package com.daesung.sales.permission.service;

import com.daesung.sales.auth.entity.AppUser;
import com.daesung.sales.auth.entity.Role;
import com.daesung.sales.auth.repository.AppUserRepository;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.permission.dto.PermissionDtos.MyPermissions;
import com.daesung.sales.permission.dto.PermissionDtos.MyScreen;
import com.daesung.sales.permission.dto.PermissionDtos.RolePermission;
import com.daesung.sales.permission.dto.PermissionDtos.ScreenRow;
import com.daesung.sales.permission.dto.PermissionDtos.UpdateRequest;
import com.daesung.sales.permission.dto.PermissionDtos.UserFlagRow;
import com.daesung.sales.permission.entity.MenuScreen;
import com.daesung.sales.permission.entity.RoleScreenPermission;
import com.daesung.sales.permission.entity.ScreenPermission;
import com.daesung.sales.permission.entity.UserPermissionFlag;
import com.daesung.sales.permission.repository.MenuScreenRepository;
import com.daesung.sales.permission.repository.RoleScreenPermissionRepository;
import com.daesung.sales.permission.repository.UserPermissionFlagRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 권한 관리(관리자 전용). 발주처 요구 —
 * "2단계(화면 단위)·3단계(사용자 개별 필드)는 모두 관리자 ID의 '사용자/권한 관리' 화면에서
 * 변경 가능하도록 구현 요청드립니다."
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PermissionService {

    private final MenuScreenRepository screenRepository;
    private final RoleScreenPermissionRepository permissionRepository;
    private final UserPermissionFlagRepository flagRepository;
    private final AppUserRepository userRepository;
    private final PermissionResolver resolver;
    private final CurrentAuditor currentAuditor;

    /** 화면 × 역할 매트릭스(2단계). 화면 순서는 매칭 순서와 같다. */
    public List<ScreenRow> matrix() {
        List<ScreenRow> rows = new ArrayList<>();
        for (MenuScreen s : screenRepository.findAllByOrderBySortOrderAscIdAsc()) {
            List<RolePermission> roles = new ArrayList<>();
            for (Role r : Role.values()) {
                ScreenPermission p = resolver.permissionOf(r, s.getId());
                roles.add(new RolePermission(r, p, p.mark()));
            }
            rows.add(new ScreenRow(s.getId(), s.getCode(), s.getName(), s.getMenuGroup(), roles));
        }
        return rows;
    }

    /**
     * 역할×화면 권한 변경.
     *
     * <p>★<b>관리자의 권한은 낮출 수 없다.</b> 관리자가 자기 발밑을 파면 권한 화면 자체에
     * 못 들어가 되돌릴 길이 없어진다 — 그때는 DB를 직접 고치는 수밖에 없다.
     */
    @Transactional
    public void update(UpdateRequest req) {
        if (req.role() == Role.ADMIN && req.permission() != ScreenPermission.WRITE) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "관리자 권한은 낮출 수 없습니다. 낮추면 권한 화면에 다시 들어올 수 없습니다.");
        }
        MenuScreen screen = screenRepository.findById(req.screenId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "화면이 없습니다. id=" + req.screenId()));
        String actor = currentAuditor.username();
        RoleScreenPermission p = permissionRepository
                .findByRoleAndScreenId(req.role(), screen.getId())
                .orElseGet(() -> permissionRepository.save(RoleScreenPermission.of(
                        req.role(), screen, ScreenPermission.NONE, actor)));
        p.change(req.permission(), actor);
        resolver.reload();   // 다음 요청부터 새 값으로 판정한다
    }



    /**
     * 로그인한 <b>본인</b>의 권한 전체(역할 + 화면별 권한 + 마감 개별권한).
     *
     * <p>★<b>왜 필요한가.</b> 권한 매트릭스는 관리자 전용이라 역할 계정은 403이고,
     * {@code /auth/me}에는 역할만 있다. 그래서 프론트가 화면별 권한을 스스로 들고 있을 수밖에
     * 없었고, 관리자가 설정을 바꿔도 역할 계정 메뉴는 그대로였다.
     *
     * <p>★<b>자기 것만 나간다.</b> 남의 권한이나 역할별 매트릭스 전체는 여기 실리지 않는다 —
     * 그건 여전히 {@code GET /permissions/screens}(관리자)의 몫이다.
     *
     * <p>‼️NONE 화면도 <b>빼지 않고</b> 담는다. 목록에 없는 것과 권한이 없는 것을
     * 프론트가 구분하지 못하면, 화면이 추가됐는데 아직 권한이 안 붙은 경우를
     * "숨김"으로 오해한다.
     */
    public MyPermissions mine(String username) {
        AppUser me = userRepository.findByUsername(username)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "사용자가 없습니다: " + username));

        List<MyScreen> screens = new ArrayList<>();
        for (MenuScreen s : screenRepository.findAllByOrderBySortOrderAscIdAsc()) {
            ScreenPermission p = resolver.permissionOf(me.getRole(), s.getId());
            screens.add(new MyScreen(s.getCode(), p, p.mark(), s.getName(), s.getMenuGroup()));
        }
        return new MyPermissions(me.getRole(), screens,
                hasFlag(username, UserPermissionFlag.PERIOD_LOCK),
                hasFlag(username, UserPermissionFlag.PERIOD_UNLOCK));
    }

    /** 사용자별 개별 권한(3단계) 목록. */
    public List<UserFlagRow> userFlags() {
        List<UserFlagRow> rows = new ArrayList<>();
        for (AppUser u : userRepository.findAll()) {
            boolean lock = false;
            boolean unlock = false;
            for (UserPermissionFlag f : flagRepository.findByUserId(u.getId())) {
                if (UserPermissionFlag.PERIOD_LOCK.equals(f.getFlagKey())) {
                    lock = f.isGranted();
                } else if (UserPermissionFlag.PERIOD_UNLOCK.equals(f.getFlagKey())) {
                    unlock = f.isGranted();
                }
            }
            rows.add(new UserFlagRow(u.getId(), u.getUsername(), u.getName(), u.getRole(), lock, unlock,
                    u.isActive()));
        }
        return rows;
    }

    @Transactional
    public void setUserFlag(Long userId, String rawFlagKey, boolean granted) {
        // ★키를 먼저 정규화한다. 화면은 조회 응답 필드명(periodLock)을 그대로 되보내는데,
        //   예전엔 그 문자열이 검증 없이 저장돼 아무도 읽지 않는 행이 생겼다(2026-09-11).
        String flagKey = UserPermissionFlag.normalizeKey(rawFlagKey);
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "사용자가 없습니다. id=" + userId));
        flagRepository.findByUserIdAndFlagKey(userId, flagKey)
                .ifPresentOrElse(
                        f -> f.change(granted, currentAuditor.username()),
                        () -> flagRepository.save(UserPermissionFlag.of(
                                userId, flagKey, granted, currentAuditor.username())));
    }

    /**
     * 이 사용자가 그 개별 권한을 가졌는가(3단계).
     *
     * <p>★관리자는 기본으로 가진다. 아무도 마감을 못 하는 상태로 시작하면
     * 권한을 부여할 사람조차 없다(최초 설정 교착).
     */
    public boolean hasFlag(String username, String flagKey) {
        AppUser u = userRepository.findByUsername(username).orElse(null);
        if (u == null) {
            return false;
        }
        return flagRepository.findByUserIdAndFlagKey(u.getId(), flagKey)
                .map(UserPermissionFlag::isGranted)
                .orElse(u.getRole() == Role.ADMIN);
    }
}
