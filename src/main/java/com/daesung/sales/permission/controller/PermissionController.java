package com.daesung.sales.permission.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.permission.dto.PermissionDtos.MyPermissions;
import com.daesung.sales.permission.dto.PermissionDtos.ScreenRow;
import com.daesung.sales.permission.dto.PermissionDtos.UpdateRequest;
import com.daesung.sales.permission.dto.PermissionDtos.UserFlagRequest;
import com.daesung.sales.permission.dto.PermissionDtos.UserFlagRow;
import com.daesung.sales.permission.service.PermissionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자/권한 관리(관리자 전용). 실제 경로: /api/v1/permissions.
 * 근거: 발주처 회신 2026-08-21 ① + 「사용자권한_구조_설계_예시(수정)」.
 *
 * <p>★이 경로만은 권한 표 밖에 있다(SecurityConfig에서 ADMIN 고정).
 * 표를 잘못 고쳐 스스로를 잠그면 되돌릴 화면이 없어지기 때문이다.
 */
@Tag(name = "관리 · 사용자/권한",
        description = "화면별 역할 권한(2단계) + 사용자 개별 권한(3단계). 관리자만")
@RestController
@RequiredArgsConstructor
@RequestMapping("/permissions")
public class PermissionController {

    private final PermissionService permissionService;

    @Operation(summary = "본인 권한 조회",
            description = """
                    로그인한 **본인**의 역할·화면별 권한·마감 개별권한을 한 번에 준다.
                    **역할과 무관하게 인증만 되면 호출할 수 있다** — 프론트가 메뉴를 그리는 근거다.

                    · `screens[].permission` 은 `NONE`(–) / `READ`(◐) / `WRITE`(○)
                    · **권한이 NONE인 화면도 목록에 담긴다.** 빼 버리면 "화면이 없는 것"과
                      "권한이 없는 것"을 프론트가 구분하지 못한다.
                    · 관리자가 권한을 바꾸면 **다음 호출부터 바로** 반영된다(배포 불필요).

                    남의 권한이나 역할별 매트릭스 전체는 여기 실리지 않는다 —
                    그건 `GET /permissions/screens`(관리자 전용)이다.""")
    @GetMapping("/me")
    public ApiResponse<MyPermissions> mine(java.security.Principal principal) {
        return ApiResponse.success(permissionService.mine(principal.getName()));
    }

    @Operation(summary = "권한 매트릭스 조회",
            description = """
                    화면 × 역할 권한(2단계). `–`(비노출) / `◐`(조회만) / `○`(조회·등록·수정).

                    · 화면 순서는 **경로 매칭 순서**와 같다. 위에 있는 화면이 먼저 걸린다
                      (예: 제품수불부가 입고/대체등록보다 앞이라, 물류가 수불부만 보는 설정이 가능하다).
                    · 판정은 **이 표 하나만** 본다. 역할×메뉴그룹 매트릭스(1단계)는
                      초기값을 만드는 규칙이라 여기 반영돼 있고, 따로 보지 않는다 —
                      둘 다 보면 서로 어긋날 때 어느 쪽이 이기는지 규칙이 하나 더 생긴다.""")
    @GetMapping("/screens")
    public ApiResponse<List<ScreenRow>> matrix() {
        return ApiResponse.success(permissionService.matrix());
    }

    @Operation(summary = "화면 권한 변경",
            description = """
                    역할 하나의 화면 권한을 바꾼다. **다음 요청부터 곧바로 적용된다**(배포 불필요) —
                    발주처 요구가 "관리자가 운영 중 자유롭게 조정"이다.

                    ⚠️**관리자 권한은 낮출 수 없다.** 관리자가 자기 발밑을 파면 이 화면에 다시
                    못 들어와 되돌릴 길이 없어진다(DB를 직접 고치는 수밖에 없다).""")
    @PutMapping("/screens")
    public ApiResponse<Void> update(@Valid @RequestBody UpdateRequest req) {
        permissionService.update(req);
        return ApiResponse.success(null);
    }

    @Operation(summary = "사용자 개별 권한 목록",
            description = """
                    3단계 — 역할과 **무관하게** 담당자에게 붙이는 권한(마감확정/마감해제).
                    같은 관리자라도 한 명은 있고 다른 한 명은 없을 수 있다.

                    ⚠️설정된 적이 없으면 **관리자는 기본 부여**로 본다.
                    아무도 마감을 못 하는 상태로 시작하면 권한을 줄 사람조차 없다.""")
    @GetMapping("/users")
    public ApiResponse<List<UserFlagRow>> userFlags() {
        return ApiResponse.success(permissionService.userFlags());
    }

    @Operation(summary = "사용자 개별 권한 변경",
            description = "`PERIOD_LOCK`(마감확정) / `PERIOD_UNLOCK`(마감해제)를 사용자별로 켜고 끈다.")
    @PutMapping("/users/{userId}")
    public ApiResponse<Void> setUserFlag(@PathVariable Long userId,
                                         @Valid @RequestBody UserFlagRequest req) {
        permissionService.setUserFlag(userId, req.flagKey(), req.granted());
        return ApiResponse.success(null);
    }
}
