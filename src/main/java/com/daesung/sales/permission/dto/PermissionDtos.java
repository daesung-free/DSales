package com.daesung.sales.permission.dto;

import com.daesung.sales.auth.entity.Role;
import com.daesung.sales.permission.entity.ScreenPermission;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** 권한 관리 화면(관리자 전용) DTO 모음. */
public final class PermissionDtos {

    private PermissionDtos() {
    }

    @Schema(name = "ScreenPermissionRow", description = "화면 하나의 역할별 권한")
    public record ScreenRow(
            @Schema(description = "화면 id") Long screenId,
            @Schema(description = "화면 코드") String code,
            @Schema(description = "화면명") String name,
            @Schema(description = "메뉴그룹") String menuGroup,
            @Schema(description = "역할별 권한(ADMIN/SALES/LOGISTICS/FINANCE)")
            List<RolePermission> roles
    ) {
    }

    @Schema(name = "RolePermission")
    public record RolePermission(
            @Schema(description = "역할") Role role,
            @Schema(description = "권한 NONE(–)/READ(◐)/WRITE(○)") ScreenPermission permission,
            @Schema(description = "표기(–/◐/○)") String mark
    ) {
    }

    @Schema(name = "PermissionUpdateRequest", description = "역할×화면 권한 변경")
    public record UpdateRequest(
            @Schema(description = "역할", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Role role,
            @Schema(description = "화면 id", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Long screenId,
            @Schema(description = "권한 NONE/READ/WRITE", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull ScreenPermission permission
    ) {
    }

    @Schema(name = "UserFlagRow", description = "사용자 개별 권한(3단계)")
    public record UserFlagRow(
            @Schema(description = "사용자 id") Long userId,
            @Schema(description = "로그인 id") String username,
            @Schema(description = "성명") String name,
            @Schema(description = "역할") Role role,
            @Schema(description = "마감확정 권한") boolean periodLock,
            @Schema(description = "마감해제 권한") boolean periodUnlock
    ) {
    }

    @Schema(name = "UserFlagUpdateRequest", description = "사용자 개별 권한 변경")
    public record UserFlagRequest(
            @Schema(description = "권한 키 PERIOD_LOCK/PERIOD_UNLOCK",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull String flagKey,
            @Schema(description = "부여 여부", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean granted
    ) {
    }
}
