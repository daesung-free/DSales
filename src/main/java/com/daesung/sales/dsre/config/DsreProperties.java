package com.daesung.sales.dsre.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * DSRE2(기존 라이브 유지) 접속 설정. daesung.dsre.*. 기본 off.
 * ★운영 접속정보는 배포 시 시크릿으로만 주입 — 소스 커밋 금지(§9).
 */
@ConfigurationProperties(prefix = "daesung.dsre")
public record DsreProperties(
        boolean enabled,
        String url,
        String username,
        String password,
        boolean readOnly
) {
}
