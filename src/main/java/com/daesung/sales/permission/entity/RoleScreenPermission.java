package com.daesung.sales.permission.entity;

import com.daesung.sales.auth.entity.Role;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 역할별 화면 권한(발주처 2단계). <b>관리자가 운영 중에 바꾼다</b> —
 * 이 표가 있는 이유가 그것이다(예전엔 SecurityConfig 코드에 박혀 있어 배포해야 바뀌었다).
 */
@Entity
@Table(name = "role_screen_permission")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RoleScreenPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "screen_id", nullable = false)
    private MenuScreen screen;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private ScreenPermission permission;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 누가 바꿨는지 — "이 권한이 언제 왜 열렸나"가 감사 질문이다. */
    @Column(name = "updated_by", length = 50)
    private String updatedBy;

    /** 표에 없던 (역할, 화면) 조합을 만든다. 초기값은 NONE — 모르는 조합을 열어 두지 않는다. */
    public static RoleScreenPermission of(Role role, MenuScreen screen,
                                          ScreenPermission permission, String actor) {
        RoleScreenPermission p = new RoleScreenPermission();
        p.role = role;
        p.screen = screen;
        p.change(permission, actor);
        return p;
    }

    public void change(ScreenPermission permission, String actor) {
        this.permission = permission;
        this.updatedAt = LocalDateTime.now();
        this.updatedBy = actor;
    }
}
