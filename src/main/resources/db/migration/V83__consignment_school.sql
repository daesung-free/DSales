-- 위탁출고에 **학교/학원**과 **출고일**. 근거: 검수피드백 PDF(2026-09-30) p.12 —
--   "위탁출고 등록 화면에서 위탁창고 선택뿐 아니라 하위 학교/학원까지 선택 가능해야 하고
--    (개념만 위탁이라 위탁창고로 빠지는 것뿐이지 실제 출고건과 동일하게 해당 학교/학원에
--    출고가 되어야 하기 때문)"
--   + "★위탁 미결 매출 확정 시에도 세부거래처별로 나누어 확정할 수 있어야 합니다★"
--
-- ★왜 미결원장 행에 다는가 — 확정(정산)이 **그 행 단위**로 일어나기 때문이다.
--   출고 전표에만 달면 "A학교 몫 70부만 확정"을 표현할 수 없다. 행이 학교별로 갈려 있어야
--   학교별 확정이 그냥 된다(정산 로직을 건드릴 필요가 없다).
--
-- ★출고일(trade_date)도 함께 둔다. 미결 조회가 "언제 나간 물건인가"를 못 보여주고 있었다.
--   기존 행은 원출고 재고이벤트의 거래일로 백필한다 — 그게 실제 출고일이다.

ALTER TABLE consignment_out
    ADD COLUMN trade_date  DATE        NULL COMMENT '출고일(원출고 재고이벤트의 거래일)',
    ADD COLUMN school_code VARCHAR(30) NULL COMMENT '학교/학원 코드 — 세부거래처별 확정의 단위',
    ADD COLUMN school_name VARCHAR(100) NULL COMMENT '학교/학원명(출고 당시 표기)';

-- 기존 행 백필: 원출고 이벤트의 거래일. 이벤트가 없는 행(이론상 없음)은 NULL 로 둔다 —
-- 임의 날짜를 넣으면 그 달 미결 집계가 틀어진다.
UPDATE consignment_out co
    JOIN inventory_txn t ON t.id = co.origin_txn_id
    SET co.trade_date = t.trade_date
    WHERE co.trade_date IS NULL;

CREATE INDEX ix_consignment_out_school ON consignment_out (school_code);
CREATE INDEX ix_consignment_out_trade_date ON consignment_out (trade_date);
