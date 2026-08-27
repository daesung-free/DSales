-- 위탁정산 임시저장(초안). 근거: 정본 13p "매출등록은 [임시저장] → [매출확정등록] 2단계" +
-- 프론트 구현(`features/sales/api.ts` saveSettlementDraft / fetchSettlementDrafts /
-- deleteSettlementDraft)이 이미 서버 API로 호출하고 있다.
--
-- ★개발 중간보고서(2026-08-18) 13p에 "임시저장·확정 2단계 구현"으로 보고됐으나
--   서버에는 없었다. 프론트가 목업으로만 돌고 있던 것을 여기서 채운다.
--
-- ★임시저장은 <b>아무것도 확정하지 않는다</b>
--   미결 잔여도, 재고도, 매출도 건드리지 않는다. 화면에 적힌 그대로 —
--   "임시저장 상태의 데이터는 매출 미반영임을 명확히 구분해야 합니다"(정본 13p).
--   그래서 이 두 테이블은 業務 원장이 아니라 <b>작성 중인 입력값</b>을 담는다.
--
-- ★초안은 계산 결과까지 함께 저장한다
--   금액을 저장하지 않고 확정 때 다시 계산하면, 그 사이 거래처 단가가 바뀌었을 때
--   담당자가 본 금액과 확정된 금액이 달라진다. 초안이 "그때 본 화면"을 재현하려면
--   수량뿐 아니라 단가·공급률·금액도 같이 남아야 한다.

CREATE TABLE settlement_draft
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_no     VARCHAR(30) NOT NULL COMMENT '초안번호(DRAFT-yyyyMMdd-n). 화면이 draftId로 쓴다',
    sales_date   DATE        NOT NULL COMMENT '매출 인식일(확정 시 이 날짜로 매출이 선다)',
    total_qty    INT         NOT NULL DEFAULT 0 COMMENT '정산수량 합',
    total_amount BIGINT      NOT NULL DEFAULT 0 COMMENT '총금액 합(공급가액+세액)',
    memo         VARCHAR(200) NULL,
    created_at   DATETIME    NOT NULL,
    created_by   VARCHAR(50) NULL,
    updated_at   DATETIME    NULL,
    updated_by   VARCHAR(50) NULL,
    deleted_at   DATETIME    NULL COMMENT '논리삭제(V25 표준)',
    deleted_by   VARCHAR(50) NULL,
    -- 논리삭제된 초안의 번호는 재사용될 수 있어야 한다 → 생성컬럼으로 UNIQUE를 살린다(V25 방식).
    -- ‼️UNIX_TIMESTAMP는 생성컬럼에 못 쓴다(비결정적 함수, MySQL 3763).
    --   V25와 같은 방식으로 COALESCE + sentinel을 쓴다:
    --   활성행 = 고정값(활성끼리만 유일) / 삭제행 = 삭제시각(서로 달라 중복 허용).
    del_key      DATETIME(6) GENERATED ALWAYS AS
                     (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED,
    CONSTRAINT uq_settlement_draft_no UNIQUE (draft_no, del_key)
) COMMENT '위탁정산 임시저장(초안) — 매출 미반영';

CREATE TABLE settlement_draft_line
(
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    draft_id           BIGINT NOT NULL,
    consignment_out_id BIGINT NOT NULL COMMENT '대상 미결. 화면의 pendingId',
    settle_qty         INT    NOT NULL COMMENT '이번에 정산할 수량(확정 전이라 미결은 그대로)',
    unit_price         INT    NULL COMMENT '정가 — 확정 때 단가가 바뀌어도 초안이 본 금액을 재현하려고 남긴다',
    supply_rate        INT    NULL COMMENT '공급률(%)',
    supply_amount      BIGINT NOT NULL DEFAULT 0 COMMENT '공급가액',
    tax                BIGINT NOT NULL DEFAULT 0 COMMENT '세액(자동산출 안 함 — 미입력 0)',
    total_amount       BIGINT NOT NULL DEFAULT 0 COMMENT '총금액',
    CONSTRAINT fk_sdl_draft FOREIGN KEY (draft_id) REFERENCES settlement_draft (id),
    CONSTRAINT fk_sdl_out FOREIGN KEY (consignment_out_id) REFERENCES consignment_out (id)
) COMMENT '초안 상세행';

CREATE INDEX ix_sdl_draft ON settlement_draft_line (draft_id);
