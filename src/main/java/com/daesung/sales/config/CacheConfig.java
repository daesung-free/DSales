package com.daesung.sales.config;

import java.time.Duration;
import java.util.Map;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis 캐시. hot-path 룩업만 캐시(①출고유형→회계구분, ②월마감 상태). 값은 JSON 직렬화.
 * ⚠️ 재고 잔량/집계/채번 등 정합성·실시간 데이터는 캐시하지 않음(단일진실=DB 원칙).
 */
@Configuration
@EnableCaching
public class CacheConfig {

    public static final String OUT_TYPE_MAPPING = "outTypeMapping";
    public static final String PERIOD_LOCK = "periodLock";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        RedisCacheConfiguration base = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair
                        .fromSerializer(new GenericJackson2JsonRedisSerializer()))
                .disableCachingNullValues()
                .entryTtl(Duration.ofHours(1));

        Map<String, RedisCacheConfiguration> perCache = Map.of(
                // 출고유형 매핑: 시드값, 사실상 불변 → 길게
                OUT_TYPE_MAPPING, base.entryTtl(Duration.ofDays(1)),
                // 월마감 상태: evict로 즉시 갱신하되, 누락 대비 안전망 TTL
                PERIOD_LOCK, base.entryTtl(Duration.ofHours(1)));

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(base)
                .withInitialCacheConfigurations(perCache)
                .build();
    }
}
