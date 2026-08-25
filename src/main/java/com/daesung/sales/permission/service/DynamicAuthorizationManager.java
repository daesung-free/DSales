package com.daesung.sales.permission.service;

import com.daesung.sales.auth.entity.Role;
import jakarta.servlet.http.HttpServletRequest;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.web.access.intercept.RequestAuthorizationContext;
import org.springframework.stereotype.Component;

/**
 * 권한 판정을 <b>표에서</b> 읽는 인가 매니저. 예전 {@code SecurityConfig}의 경로별
 * {@code hasRole(...)} 나열을 대체한다 — 발주처 요구가 "관리자가 운영 중 자유롭게 조정"이라
 * 코드에 박아 두면 배포 없이는 못 바꾼다.
 *
 * <p>인증 여부는 여기서 보지 않는다. 미인증은 필터 단계에서 401로 걸러지고,
 * 여기 오는 것은 이미 인증된 요청이다.
 */
@Component
@RequiredArgsConstructor
public class DynamicAuthorizationManager
        implements AuthorizationManager<RequestAuthorizationContext> {

    private static final String API_PREFIX = "/api/v1";

    private final PermissionResolver resolver;

    @Override
    public AuthorizationDecision check(Supplier<Authentication> authentication,
                                       RequestAuthorizationContext context) {
        Authentication auth = authentication.get();
        if (auth == null || !auth.isAuthenticated()) {
            return new AuthorizationDecision(false);
        }
        Role role = roleOf(auth);
        if (role == null) {
            return new AuthorizationDecision(false);
        }
        HttpServletRequest req = context.getRequest();
        String path = stripPrefix(req.getRequestURI());
        boolean write = isWrite(req.getMethod());
        return new AuthorizationDecision(resolver.allows(role, path, write));
    }

    /** 권한 문자열(ROLE_XXX) → 역할. 우리 토큰은 역할을 하나만 담는다. */
    private static Role roleOf(Authentication auth) {
        for (GrantedAuthority a : auth.getAuthorities()) {
            String v = a.getAuthority();
            if (v.startsWith("ROLE_")) {
                try {
                    return Role.valueOf(v.substring(5));
                } catch (IllegalArgumentException e) {
                    return null;   // 없어진 역할(예: 폐지된 VIEWER)의 옛 토큰
                }
            }
        }
        return null;
    }

    /** 표에는 {@code /api/v1} 이후 경로만 담는다 — 접두어가 바뀌어도 데이터를 안 고치게. */
    private static String stripPrefix(String uri) {
        return uri.startsWith(API_PREFIX) ? uri.substring(API_PREFIX.length()) : uri;
    }

    private static boolean isWrite(String method) {
        return "POST".equals(method) || "PUT".equals(method)
                || "DELETE".equals(method) || "PATCH".equals(method);
    }
}
