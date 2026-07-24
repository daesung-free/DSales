-- 인증/RBAC(신규 전면 설계). 근거: 요구사항정의서 DB-29/BE-61/FE-11, API 스펙 "JWT Bearer + role".
-- 레거시 결함 근절: 비밀번호 BCrypt 해시(평문 금지), IP기반 판별 폐기.

-- 직원 계정. role은 부서 역할(권한 매트릭스 세부는 발주처 확정 대기).
CREATE TABLE app_user (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    username   VARCHAR(50)  NOT NULL UNIQUE,
    password   VARCHAR(100) NOT NULL,          -- BCrypt 해시
    name       VARCHAR(50)  NOT NULL,
    role       VARCHAR(20)  NOT NULL
                 CHECK (role IN ('ADMIN','FINANCE','LOGISTICS','SALES','VIEWER')),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(50),
    updated_at TIMESTAMP,
    updated_by VARCHAR(50)
);

-- refresh 토큰(회전·무효화용). 토큰 원문 대신 해시 저장. Redis 대신 DB(내부 백오피스).
CREATE TABLE refresh_token (
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT       NOT NULL REFERENCES app_user(id),
    token_hash VARCHAR(100) NOT NULL UNIQUE,   -- refresh 토큰의 해시
    expires_at TIMESTAMP    NOT NULL,
    revoked    BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    created_by VARCHAR(50),
    updated_at TIMESTAMP,
    updated_by VARCHAR(50)
);
CREATE INDEX ix_refresh_user ON refresh_token (user_id);
