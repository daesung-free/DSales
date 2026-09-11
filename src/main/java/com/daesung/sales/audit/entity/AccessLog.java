package com.daesung.sales.audit.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 행위 기록. 근거: 발주처 요청(2026-09-11) "직원의 다운로드기록같은걸 볼 수 있게".
 *
 * <p>★<b>추가만 한다.</b> 수정·삭제 메서드를 두지 않는다 — 고칠 수 있는 기록은 증거가 되지 못한다.
 * 보존기간이 지난 것만 배치가 일괄 삭제한다.
 *
 * <p>★역할을 함께 박아 둔다. 사용자 테이블을 조인하면 <b>지금</b> 역할이 나오는데,
 * 알아야 하는 것은 <b>그때</b> 무슨 권한으로 했는지다.
 */
@Entity
@Table(name = "access_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AccessLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 50)
    private String username;

    @Column(length = 20)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AccessAction action;

    @Column(length = 60)
    private String menu;

    @Column(nullable = false, length = 10)
    private String method;

    @Column(nullable = false, length = 300)
    private String path;

    @Column(length = 500)
    private String query;

    @Column(nullable = false)
    private int status;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "file_name", length = 200)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "client_ip", length = 45)
    private String clientIp;

    @Column(name = "user_agent", length = 300)
    private String userAgent;

    @Column(name = "took_ms")
    private Integer tookMs;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public static AccessLog of(String username, String role, AccessAction action, String menu,
                               String method, String path, String query, int status,
                               String fileName, Long fileSize,
                               String clientIp, String userAgent, Integer tookMs) {
        AccessLog a = new AccessLog();
        a.username = username;
        a.role = role;
        a.action = action;
        a.menu = menu;
        a.method = method;
        a.path = path;
        a.query = query;
        a.status = status;
        a.success = status >= 200 && status < 300;
        a.fileName = fileName;
        a.fileSize = fileSize;
        a.clientIp = clientIp;
        a.userAgent = userAgent;
        a.tookMs = tookMs;
        a.createdAt = LocalDateTime.now();
        return a;
    }
}
