-- 기초정보(거래처·상품·거래처별단가) 변경 이력.
-- 근거: 발주처 확정(자료요청서 3-1 라 회신) —
--   "기초정보(거래처·상품·단가)는 연 1~2회 일괄 등록하고 이후 수정은 거의 없지만,
--    혹시 중간에 잘못 수정이 된 경우를 발견하기 위해 변경 이력도 함께 포함되면 좋겠다."
--
-- ★status_history(V31)와 나누는 이유
--   그쪽은 '상태축'(취소·잠금·활성) 전용이라 from/to가 VARCHAR(30)이다.
--   기초정보는 거래처명·주소·비고처럼 긴 값이 바뀌므로 담기지 않는다.
--   질의 성격도 다르다 — 상태는 "언제 잠갔나", 기초정보는 "이 값이 언제 뭐에서 뭐로 바뀌었나".
--
-- ★기록 범위: 수정(UPDATE)만. 생성은 created_by/created_at이 이미 답하고,
--   비활성/삭제는 status_history 소관이다. 발주처 목적도 "잘못 수정된 것을 찾는 것"이다.
--   바뀐 필드만 행을 만든다 — 저장 버튼 한 번에 전 필드를 남기면 실제 변경이 묻힌다.

CREATE TABLE master_change_log (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    entity_type VARCHAR(30)  NOT NULL COMMENT '대상 PARTNER(거래처)/PRODUCT(도서)/PARTNER_PRICE(거래처별단가)',
    entity_id   BIGINT       NOT NULL COMMENT '대상 id',
    entity_code VARCHAR(100) NULL     COMMENT '대상 식별코드(거래처코드·도서코드 등). id만으로는 사람이 못 읽는다',
    field       VARCHAR(50)  NOT NULL COMMENT '바뀐 필드명',
    field_label VARCHAR(100) NULL     COMMENT '화면에 보이는 한글 항목명',
    old_value   VARCHAR(500) NULL     COMMENT '이전 값',
    new_value   VARCHAR(500) NULL     COMMENT '이후 값',
    changed_by  VARCHAR(50)  NOT NULL COMMENT '변경자(로그인 사용자)',
    changed_at  DATETIME(6)  NOT NULL COMMENT '변경 시각',
    created_at  DATETIME     NOT NULL,
    created_by  VARCHAR(50)  NULL,
    updated_at  DATETIME     NULL,
    updated_by  VARCHAR(50)  NULL
) COMMENT '기초정보 변경 이력(감사용). 추가만 하고 수정·삭제하지 않는다';

-- 대상 하나의 변경 흐름(가장 잦은 질의)
CREATE INDEX ix_master_change_entity ON master_change_log (entity_type, entity_id, changed_at);
-- "이 사람이 이 기간에 무엇을 바꿨나"
CREATE INDEX ix_master_change_actor ON master_change_log (changed_by, changed_at);
-- 기간 전체 조회
CREATE INDEX ix_master_change_at ON master_change_log (changed_at);
