-- 같은 요청을 한 번만 처리하기 위한 기록(Idempotency-Key).
--
-- 근거: 프론트 실측 2026-10-22 ③-① — 위탁 매출 확정 중 연결이 끊기거나 504 가 나면
--   사용자는 다시 누르고, **초과정산 차단은 발주처 확정으로 걷어냈기 때문에**(2026-08-31)
--   서버가 막아 주지 않는다 → 매출이 두 번 잡힌다.
--   프론트가 "반영됐을 수도 있음" 안내로 덮고 있었지만, 그건 덮은 것이지 막은 것이 아니다.
--
-- ★응답 본문을 통째로 저장한다. 타임아웃 난 클라이언트는 **첫 응답을 받지 못했으므로**,
--   재시도에 "이미 처리됨"만 돌려주면 결과(전표번호·매출번호)를 영영 모른다.
--   같은 키로 다시 부르면 **처음과 똑같은 응답**이 나가야 재시도가 안전해진다.
CREATE TABLE idempotency_record (
    idem_key      VARCHAR(100) NOT NULL PRIMARY KEY COMMENT '클라이언트가 보낸 Idempotency-Key',
    scope         VARCHAR(40)  NOT NULL COMMENT '용도 구분(예: CONSIGN_SETTLE) — 키 충돌 진단용',
    status        VARCHAR(12)  NOT NULL COMMENT 'IN_PROGRESS / DONE',
    response_json MEDIUMTEXT   NULL     COMMENT 'DONE 일 때 첫 응답 본문',
    created_at    TIMESTAMP    NOT NULL,
    updated_at    TIMESTAMP    NULL,
    created_by    VARCHAR(50)  NULL,
    CONSTRAINT ck_idem_status CHECK (status IN ('IN_PROGRESS', 'DONE'))
) COMMENT '요청 1회 처리 보장 기록';

-- 오래된 키 정리용(보존기간 정책이 정해지면 배치가 이 인덱스로 지운다).
CREATE INDEX ix_idem_created ON idempotency_record (created_at);
