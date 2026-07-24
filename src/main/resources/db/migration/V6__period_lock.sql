-- 월마감(period_locks). 근거: 요구사항정의서 3대 로직/DB-30(완전 신규). 레거시 참고구현 없음.
-- 잠근 월(period_year, period_month)의 재무 쓰기(매출 등록/취소·위탁정산·수금)를 차단 → PERIOD_LOCKED.
-- 정책 세부(확정 권한자/재오픈 정책)는 발주처 확인 대상 — 현재는 오픈 권한.
CREATE TABLE period_lock (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    period_year  INTEGER NOT NULL,
    period_month INTEGER NOT NULL CHECK (period_month BETWEEN 1 AND 12),
    locked       BOOLEAN NOT NULL DEFAULT FALSE,
    locked_at    TIMESTAMP,
    locked_by    VARCHAR(50),
    memo         VARCHAR(500),
    created_at   TIMESTAMP NOT NULL,
    created_by   VARCHAR(50),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(50),
    CONSTRAINT uq_period_lock UNIQUE (period_year, period_month)
);
