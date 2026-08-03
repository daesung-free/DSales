package com.daesung.sales.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 배치 스케줄러 활성화. 근거: 개발문서 19.0(배치잡 인프라) — 레거시엔 배치 자체가 없었다(BE-63).
 * 테스트에서는 스케줄 자동실행이 결과를 오염시키므로 daesung.batch.enabled=false로 끈다.
 */
@Configuration
@EnableScheduling
@org.springframework.boot.autoconfigure.condition.ConditionalOnProperty(
        name = "daesung.batch.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
