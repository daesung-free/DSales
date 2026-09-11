-- 권한거부(403) 기록을 access_log에 남기기 시작했다(2026-09-11 점검).
-- 값만 늘었을 뿐 컬럼 타입은 그대로다 — VARCHAR(20)에 'DENIED'가 들어간다.
-- 주석이 곧 이 표를 읽는 사람의 설명서라, 값 목록을 실제와 맞춰 둔다.
ALTER TABLE access_log
    MODIFY COLUMN action VARCHAR(20) NOT NULL
        COMMENT 'DOWNLOAD / LOGIN / LOGOUT / CREATE / UPDATE / DELETE / DENIED(권한거부 403)';
