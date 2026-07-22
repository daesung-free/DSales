package com.daesung.sales.auth.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** refresh 토큰(회전·무효화). 원문 대신 해시 저장. 로그아웃/재발급 통제용. */
@Entity
@Table(name = "refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "token_hash", nullable = false, unique = true, length = 100)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    public static RefreshToken issue(Long userId, String tokenHash, LocalDateTime expiresAt) {
        RefreshToken t = new RefreshToken();
        t.userId = userId;
        t.tokenHash = tokenHash;
        t.expiresAt = expiresAt;
        t.revoked = false;
        return t;
    }

    public void revoke() {
        this.revoked = true;
    }

    public boolean isValidNow(LocalDateTime now) {
        return !revoked && expiresAt.isAfter(now);
    }
}
