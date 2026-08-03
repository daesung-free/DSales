package com.daesung.sales.common.audit;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * 현재 작업 주체(로그인 사용자명). 없으면 'system'.
 *
 * <p>JPA Auditing(created_by/updated_by)과 논리삭제(deleted_by)가 같은 판정을 쓰도록 한 곳으로 모았다.
 * 감사 주체 판정 로직이 두 군데로 갈라지면 "누가 지웠는지"가 감사컬럼과 어긋난다.
 */
@Component
public class CurrentAuditor {

    public static final String SYSTEM = "system";

    /** 현재 로그인 사용자명. 인증 컨텍스트가 없으면(배치·부트스트랩) 'system'. */
    public String username() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated() || auth.getName() == null) {
            return SYSTEM;
        }
        return auth.getName();
    }
}
