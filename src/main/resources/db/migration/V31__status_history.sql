-- 상태변경 이력. 근거: 개발문서 5.0 "상태변경이력 테이블" · 리스크 12.0 "감사로그·상태변경이력 전무,
-- 보안심사 시 지적 가능성" · 갭분석 BE-3A "UPDATE 제자리 덮어쓰기로 이전 상태 소실".
--
-- 지금까지는 상태를 덮어써서 "현재 값"만 알 수 있었다. updated_by/updated_at이 있지만 마지막 변경자만
-- 남아, 잠금→해제→재잠금처럼 같은 축이 여러 번 바뀌면 중간 기록이 사라진다.
--
-- 대상을 테이블별로 쪼개지 않고 하나로 모은 이유: 감사에서 필요한 질문이 "이 사람이 무엇을 바꿨나"라서
-- 대상별로 흩어져 있으면 답할 수 없다.

CREATE TABLE status_history (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type  VARCHAR(30)  NOT NULL COMMENT '대상 종류 SALE/PERIOD_LOCK/CONSIGNMENT_OUT/SCHOOL/APP_USER',
    entity_id    BIGINT       NOT NULL COMMENT '대상 id',
    field        VARCHAR(30)  NOT NULL COMMENT '상태축(canceled/locked/status/active) — 한 대상에 상태가 여럿일 수 있다',
    from_status  VARCHAR(30)  NULL COMMENT '이전 값(최초 생성이면 NULL)',
    to_status    VARCHAR(30)  NOT NULL COMMENT '이후 값',
    reason       VARCHAR(500) NULL COMMENT '변경 사유 — 감사에서 실제로 묻는 것은 "왜"다',
    changed_by   VARCHAR(50)  NOT NULL COMMENT '변경자(로그인 사용자)',
    changed_at   DATETIME(6)  NOT NULL COMMENT '변경 시각',
    created_at   DATETIME     NOT NULL,
    created_by   VARCHAR(50)  NULL,
    updated_at   DATETIME     NULL,
    updated_by   VARCHAR(50)  NULL
) COMMENT '상태변경 이력(감사용). 추가만 하고 수정·삭제하지 않는다';

-- 대상 하나의 변경 흐름 조회(가장 잦은 질의)
CREATE INDEX ix_status_history_entity ON status_history (entity_type, entity_id, changed_at);
-- "이 사람이 이 기간에 무엇을 바꿨나"
CREATE INDEX ix_status_history_actor ON status_history (changed_by, changed_at);
-- 기간 전체 조회
CREATE INDEX ix_status_history_at ON status_history (changed_at);
