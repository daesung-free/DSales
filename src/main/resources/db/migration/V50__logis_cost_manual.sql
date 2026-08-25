-- 물류작업비 수기 등록(28p 에디팅 모드). 근거: 발주처 회신 2026-08-21 —
-- "Page No.28 물류작업비계산 화면에 '에디팅' 모드를 추가해 주시기 바랍니다.
--  에디팅 설정 후 행 우클릭으로 물류비(작업비 등)를 수기 등록·수정·삭제·복사할 수 있는 기능입니다.
--  기존 출고 작업비 자동계산 로직에는 영향이 없어야 하며,
--  수기 등록 건에 대한 소계/누계/합계/총계가 모두 정상 반영되어야 합니다."
--
-- ★왜 별도 표인가 — 자동계산분을 직접 고칠 수 없다
--   자동계산분은 우리가 저장한 값이 아니라 조회할 때마다 DSRE2에서 다시 만드는 값이다.
--   거기에 손을 대도 다음 조회에서 새로 계산되면서 수정이 날아간다.
--   수기 행을 따로 두고 조회 시 합치는 것이 "자동계산 로직에는 영향이 없어야" 한다는 요구와도 맞는다.
--
-- ★마감월은 손대지 못한다
--   등록·수정·삭제 모두 월마감을 검사한다. 마감된 달의 금액이 나중에 바뀌면
--   과거 작업비 고정 원칙(V49)이 수기 행으로 뚫린다.
--
-- ★귀속 축은 접수일자 — 자동계산분과 같아야 한 화면에서 합쳐진다.

CREATE TABLE logis_cost_manual
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    req_date     DATE         NOT NULL COMMENT '접수일자(귀속 축). 자동계산분과 같은 기준',
    req_cd       INT          NOT NULL DEFAULT 0 COMMENT '신청번호. 수기 건은 대응 신청이 없을 수 있어 0 허용',
    product_code VARCHAR(20)  NULL,
    product_name VARCHAR(100) NULL,
    grade        VARCHAR(10)  NULL,
    dtl_cd       INT          NOT NULL DEFAULT 0 COMMENT '시행코드. 없으면 0',
    detail_name  VARCHAR(100) NULL,
    partner_code VARCHAR(10)  NULL,
    partner_name VARCHAR(100) NULL,

    material_qty BIGINT       NOT NULL DEFAULT 0,
    paper_qty    BIGINT       NOT NULL DEFAULT 0,
    paper_amount BIGINT       NOT NULL DEFAULT 0,
    omr_qty      BIGINT       NOT NULL DEFAULT 0,
    omr_amount   BIGINT       NOT NULL DEFAULT 0,
    etc_qty      BIGINT       NOT NULL DEFAULT 0,
    etc_amount   BIGINT       NOT NULL DEFAULT 0,
    inwon        INT          NOT NULL DEFAULT 0,
    basic_amount BIGINT       NOT NULL DEFAULT 0,
    trade_amount BIGINT       NOT NULL DEFAULT 0,

    apply_gn     VARCHAR(5)   NULL COMMENT '구분(S=일반/A=사고). 자동계산분과 같은 축으로 걸러지게 한다',
    memo         VARCHAR(500) NULL COMMENT '왜 수기로 넣었는지 — 나중에 이 금액을 물을 때 답이 된다',

    created_at   DATETIME     NOT NULL,
    created_by   VARCHAR(50)  NULL,
    updated_at   DATETIME     NULL,
    updated_by   VARCHAR(50)  NULL,
    deleted_at   DATETIME(6)  NULL COMMENT '논리삭제 — 지운 금액이 왜 사라졌는지 남아야 한다',
    deleted_by   VARCHAR(50)  NULL
) COMMENT '수기 등록 물류작업비(28p 에디팅). 자동계산분과 조회 시 합쳐진다';

CREATE INDEX ix_logis_manual_date ON logis_cost_manual (req_date, deleted_at);
