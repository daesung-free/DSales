package com.daesung.sales.auth.controller;

import com.daesung.sales.auth.config.JwtProperties;
import com.daesung.sales.auth.dto.AuthDtos.LoginRequest;
import com.daesung.sales.auth.dto.AuthDtos.RefreshRequest;
import com.daesung.sales.auth.dto.AuthDtos.TokenResponse;
import com.daesung.sales.auth.dto.AuthDtos.UserCreateRequest;
import com.daesung.sales.auth.dto.AuthDtos.UserResponse;
import com.daesung.sales.auth.service.AuthService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.security.Principal;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.web.bind.annotation.CookieValue;
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

    /** refresh 토큰 쿠키명·경로(refresh 계열 엔드포인트에만 전송). */
    private static final String REFRESH_COOKIE = "refresh_token";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final AuthService authService;
    private final JwtProperties jwtProperties;

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

    @Operation(summary = "로그인",
            description = "아이디/비밀번호 → access(JWT)는 바디, refresh는 httpOnly 쿠키로 발급(XSS 방어). "
                    + "응답 바디의 refreshToken은 null.")
    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest req, HttpServletResponse response) {
        return ApiResponse.success(issueWithCookie(authService.login(req), response));
    }

    @Operation(summary = "토큰 재발급",
            description = "refresh 토큰(httpOnly 쿠키, 없으면 바디)으로 새 access+refresh 발급(회전). "
                    + "기존 refresh 무효화 + 재사용(탈취) 탐지 시 전체 세션 무효화. 새 refresh는 다시 쿠키로.")
    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(
            @CookieValue(name = REFRESH_COOKIE, required = false) String cookieToken,
            @RequestBody(required = false) RefreshRequest req,
            HttpServletResponse response) {
        String token = (cookieToken != null && !cookieToken.isBlank())
                ? cookieToken
                : (req != null ? req.refreshToken() : null);
        if (token == null || token.isBlank()) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED, "refresh 토큰이 없습니다.");
        }
        return ApiResponse.success(issueWithCookie(authService.refresh(token), response));
    }

    @Operation(summary = "내 정보 조회",
            description = "현재 로그인 사용자의 아이디·이름·역할. 프론트 메뉴 노출 제어(RBAC)용.")
    @org.springframework.web.bind.annotation.GetMapping("/me")
    public ApiResponse<UserResponse> me(Principal principal) {
        return ApiResponse.success(authService.me(principal.getName()));
    }

    @Operation(summary = "로그아웃",
            description = "현재 사용자의 모든 refresh 토큰 무효화 + refresh 쿠키 삭제(access는 만료까지 유효).")
    @PostMapping("/logout")
    public ApiResponse<Void> logout(Principal principal, HttpServletResponse response) {
        authService.logout(principal.getName());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie("", Duration.ZERO).toString());
        return ApiResponse.success(null);
    }

    /** refresh는 httpOnly 쿠키로 심고, 응답 바디에서는 refreshToken을 제거(null)한다. */
    private TokenResponse issueWithCookie(TokenResponse t, HttpServletResponse response) {
        Duration maxAge = Duration.ofDays(jwtProperties.refreshTokenDaysOrDefault());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie(t.refreshToken(), maxAge).toString());
        return new TokenResponse(t.accessToken(), null, t.tokenType(),
                t.username(), t.role(), t.accessExpiresInMinutes());
    }

    private ResponseCookie refreshCookie(String value, Duration maxAge) {
        return ResponseCookie.from(REFRESH_COOKIE, value)
                .httpOnly(true)
                .secure(jwtProperties.cookieSecureOrDefault())
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
