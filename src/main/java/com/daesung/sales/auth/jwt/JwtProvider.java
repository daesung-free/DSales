package com.daesung.sales.auth.jwt;

import com.daesung.sales.auth.config.JwtProperties;
import com.daesung.sales.auth.entity.Role;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.Jwts;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** access 토큰(JWT) 발급·검증. 서명키는 설정 주입(secret ≥32bytes). */
@Component
public class JwtProvider {

    private static final Logger log = LoggerFactory.getLogger(JwtProvider.class);
    /** application.yml의 로컬 개발용 더미 시크릿(운영 배포 시 절대 사용 금지). */
    private static final String DUMMY_SECRET = "local-dev-only-change-me-please-32bytes-minimum-secret-key";

    private final SecretKey key;
    private final long accessMinutes;

    public JwtProvider(JwtProperties props) {
        String secret = props.jwtSecret();
        // fail-fast: 미설정/약한 키 차단(조용한 폴백 제거). <32byte는 Keys.hmacShaKeyFor가 예외.
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT 시크릿(daesung.security.jwt-secret) 미설정 — 기동 불가.");
        }
        if (DUMMY_SECRET.equals(secret)) {
            log.error("★★★ 기본 더미 JWT 시크릿 사용 중 — 토큰 위조(관리자 사칭) 위험. "
                    + "운영 배포 시 반드시 DAESUNG_SECURITY_JWTSECRET 환경변수로 실제 시크릿 주입할 것. ★★★");
        }
        // 쿠키 정책(SameSite=None인데 Secure가 꺼져 있음)도 여기서 함께 막는다 —
        // 런타임에 드러나면 "30분 뒤 로그아웃된다"는 증상으로만 보여 원인을 못 찾는다.
        props.assertCookiePolicy();
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessMinutes = props.accessTokenMinutesOrDefault();
    }

    /** access 토큰 발급. subject=username, claims: uid, role. */
    public String generateAccessToken(Long userId, String username, Role role) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(username)
                .claim("uid", userId)
                .claim("role", role.name())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plusSeconds(accessMinutes * 60)))
                .signWith(key)
                .compact();
    }

    /** 서명·만료 검증 후 Claims 반환. 실패 시 예외(JwtException). */
    public Jws<Claims> parse(String token) {
        return Jwts.parser().verifyWith(key).build().parseSignedClaims(token);
    }

    public long accessMinutes() {
        return accessMinutes;
    }
}
