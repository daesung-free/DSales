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
import org.springframework.stereotype.Component;

/** access 토큰(JWT) 발급·검증. 서명키는 설정 주입(secret ≥32bytes). */
@Component
public class JwtProvider {

    private final SecretKey key;
    private final long accessMinutes;

    public JwtProvider(JwtProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwtSecret().getBytes(StandardCharsets.UTF_8));
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
