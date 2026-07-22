package com.daesung.sales.auth.service;

import com.daesung.sales.auth.config.JwtProperties;
import java.time.Duration;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * refresh 토큰 저장소(Redis). TTL 자동만료 → 만료 정리 배치 불필요.
 *  - 토큰 키: refresh:token:{hash} = userId   (TTL = refresh 기간)
 *  - 사용자 집합: refresh:user:{userId} = {hash...}  (로그아웃 시 일괄 무효화용)
 * 원문 대신 해시만 저장(유출 대비). Redis 죽으면 전원 재로그인(refresh는 휘발성이라 허용 범위).
 */
@Component
@RequiredArgsConstructor
public class RefreshTokenStore {

    private static final String TOKEN_KEY = "refresh:token:";
    private static final String USER_KEY = "refresh:user:";

    private final StringRedisTemplate redis;
    private final JwtProperties jwtProperties;

    private Duration ttl() {
        return Duration.ofDays(jwtProperties.refreshTokenDaysOrDefault());
    }

    /** 발급: 토큰 해시→userId 저장 + 사용자 집합에 추가(둘 다 TTL). */
    public void save(Long userId, String tokenHash) {
        Duration ttl = ttl();
        redis.opsForValue().set(TOKEN_KEY + tokenHash, userId.toString(), ttl);
        String userKey = USER_KEY + userId;
        redis.opsForSet().add(userKey, tokenHash);
        redis.expire(userKey, ttl);
    }

    /** 유효한 refresh면 userId 반환, 없으면(만료·무효) null. */
    public Long findUserId(String tokenHash) {
        String v = redis.opsForValue().get(TOKEN_KEY + tokenHash);
        return (v == null) ? null : Long.valueOf(v);
    }

    /** 회전: 특정 토큰 하나 무효화(재발급 시 기존 토큰 폐기). */
    public void revoke(String tokenHash) {
        String v = redis.opsForValue().get(TOKEN_KEY + tokenHash);
        if (v != null) {
            redis.delete(TOKEN_KEY + tokenHash);
            redis.opsForSet().remove(USER_KEY + v, tokenHash);
        }
    }

    /** 로그아웃: 사용자의 모든 refresh 무효화. */
    public void revokeAllByUser(Long userId) {
        String userKey = USER_KEY + userId;
        Set<String> hashes = redis.opsForSet().members(userKey);
        if (hashes != null) {
            hashes.forEach(h -> redis.delete(TOKEN_KEY + h));
        }
        redis.delete(userKey);
    }
}
