package com.daesung.sales.auth.dto;

import com.daesung.sales.auth.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** 인증 관련 요청/응답 DTO 모음. */
public final class AuthDtos {

    private AuthDtos() {
    }

    public record LoginRequest(
            @Schema(description = "아이디", example = "admin", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String username,
            @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String password) {
    }

    public record RefreshRequest(
            @Schema(description = "refresh 토큰", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String refreshToken) {
    }

    public record UserCreateRequest(
            @Schema(description = "아이디", example = "finance01", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String username,
            @Schema(description = "비밀번호", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String password,
            @Schema(description = "이름", example = "김재무", requiredMode = Schema.RequiredMode.REQUIRED)
            @NotBlank String name,
            @Schema(description = "역할(ADMIN/FINANCE/LOGISTICS/SALES/VIEWER)", example = "FINANCE",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Role role) {
    }

    public record TokenResponse(
            @Schema(description = "access 토큰(JWT)") String accessToken,
            @Schema(description = "refresh 토큰") String refreshToken,
            @Schema(description = "토큰 타입") String tokenType,
            @Schema(description = "아이디") String username,
            @Schema(description = "역할") String role,
            @Schema(description = "access 토큰 유효(분)") long accessExpiresInMinutes) {
    }

    public record UserActiveRequest(
            @Schema(description = "true=사용, false=중지", example = "false",
                    requiredMode = Schema.RequiredMode.REQUIRED)
            @NotNull Boolean active) {
    }

    public record UserResponse(
            @Schema(description = "사용자 id") Long id,
            @Schema(description = "아이디") String username,
            @Schema(description = "이름") String name,
            @Schema(description = "역할") Role role,
            @Schema(description = "사용 여부 — false면 로그인·토큰재발급이 막힌다") boolean active) {
    }
}
