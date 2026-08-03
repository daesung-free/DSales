package com.daesung.sales.config;

import com.daesung.sales.common.audit.CurrentAuditor;
import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * JPA Auditing 활성화. created_by/updated_by = 현재 로그인 사용자, 없으면 'system'.
 * 주체 판정은 {@link CurrentAuditor} 단일 소스 — 논리삭제 deleted_by와 같은 기준을 쓴다.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {

    @Bean
    public AuditorAware<String> auditorProvider(CurrentAuditor currentAuditor) {
        return () -> Optional.of(currentAuditor.username());
    }
}
