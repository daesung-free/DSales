package com.daesung.sales.auth.controller;

import com.daesung.sales.auth.dto.AuthDtos.LoginRequest;
import com.daesung.sales.auth.dto.AuthDtos.RefreshRequest;
import com.daesung.sales.auth.dto.AuthDtos.TokenResponse;
import com.daesung.sales.auth.dto.AuthDtos.UserCreateRequest;
import com.daesung.sales.auth.dto.AuthDtos.UserResponse;
import com.daesung.sales.auth.service.AuthService;
import com.daesung.sales.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 인증/계정. 실제 경로: /api/v1/auth. login/refresh/bootstrap-admin은 공개, users는 관리자. */
@Tag(name = "인증 · 계정", description = "로그인/토큰재발급/로그아웃 + 계정 생성(RBAC)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    @Operation(summary = "최초 관리자 생성",
            description = "계정이 하나도 없을 때만 허용(부트스트랩). 이후 400. 생성 후 login으로 토큰 발급.")
    @PostMapping("/bootstrap-admin")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> bootstrapAdmin(@Valid @RequestBody UserCreateRequest req) {
        return ApiResponse.success(authService.bootstrapAdmin(req));
    }

    @Operation(summary = "계정 생성(관리자 전용)", description = "역할 지정해 직원 계정 생성. ROLE_ADMIN만 호출 가능.")
    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> createUser(@Valid @RequestBody UserCreateRequest req) {
        return ApiResponse.success(authService.createUser(req));
    }

    @Operation(summary = "로그인", description = "아이디/비밀번호 → access(JWT)+refresh 토큰 발급.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.success(authService.login(req));
    }

    @Operation(summary = "토큰 재발급", description = "refresh 토큰으로 새 access+refresh 발급(회전). 기존 refresh는 무효화.")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest req) {
        return ApiResponse.success(authService.refresh(req.refreshToken()));
    }

    @Operation(summary = "로그아웃", description = "현재 사용자의 모든 refresh 토큰 무효화(access는 만료까지 유효).")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Principal principal) {
        authService.logout(principal.getName());
        return ApiResponse.success(null);
    }
}
