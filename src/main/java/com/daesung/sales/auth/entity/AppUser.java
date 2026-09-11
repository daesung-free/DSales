package com.daesung.sales.auth.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 직원 계정. 비밀번호는 BCrypt 해시(평문 금지). role=부서 역할. */
@Entity
@Table(name = "app_user")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AppUser extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 50)
    private String username;

    @Column(nullable = false, length = 100)
    private String password;   // BCrypt 해시

    @Column(nullable = false, length = 50)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Role role;

    @Column(nullable = false)
    private boolean active = true;

    public static AppUser create(String username, String encodedPassword, String name, Role role) {
        AppUser u = new AppUser();
        u.username = username;
        u.password = encodedPassword;
        u.name = name;
        u.role = role;
        u.active = true;
        return u;
    }

    public void changePassword(String encodedPassword) {
        this.password = encodedPassword;
    }

    /**
     * 계정 사용 중지. 로그인·토큰재발급이 막힌다({@code AuthService}가 {@code isActive}로 거른다).
     *
     * <p>★행을 지우지 않는다 — {@code created_by}·{@code updated_by}가 아이디를 가리키고 있어
     * 지우면 과거 기록의 작성자를 잃는다. 계정은 <b>끄는</b> 것이지 지우는 것이 아니다.
     * ‼️이미 발급된 access 토큰은 만료(30분)까지 살아 있다 — stateless JWT라 즉시 끊으려면
     * refresh를 폐기해 재발급을 막는 것이 최선이다({@code AuthService.setActive}가 같이 한다).
     */
    public void deactivate() {
        this.active = false;
    }

    /** 사용 재개. */
    public void activate() {
        this.active = true;
    }
}
