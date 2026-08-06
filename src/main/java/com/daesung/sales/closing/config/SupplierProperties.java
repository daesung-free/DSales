package com.daesung.sales.closing.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 계산서(홈택스) 공급자(자사) 정보. daesung.supplier.* 설정에서 주입.
 * ★실값(사업자번호/주소 등)은 배포 시 환경변수/시크릿으로만 주입 — 소스 커밋 금지(§9).
 * TODO: 2법인(대성학력개발연구소/리브커넥스) 분리 주입.
 */
@ConfigurationProperties(prefix = "daesung.supplier")
public record SupplierProperties(
        String bizNo,
        String name,
        String bossName,
        String addr,
        String bizStatus,
        String bizItem,
        String tel
) {
}
