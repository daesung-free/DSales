-- 폐기·입고 취소. 근거: 발주처 회신 「삭제권한」 —
--   "마감 확정 전에는 등록 담당자가 잘못 등록한 건을 삭제할 수 있어야 합니다.
--    마감 확정 후에는 물리 삭제 없이 취소 처리로 남기는 방향"
--
-- ★<b>물리 삭제하지 않는다.</b> 재고는 inventory_txn(이벤트 로그)이 유일 진실이라,
--   지우면 "언제 왜 되돌렸나"가 사라진다. 반대 부호 이벤트를 새로 적어 상쇄한다 —
--   매출취소(reverseShipments)와 같은 규율이다.
--
-- ★왜 별도 표인가 — 폐기·입고는 머리 테이블이 없다
--   매출은 sales 행에 canceled 플래그를 둘 수 있었지만, 폐기·입고는 inventory_txn 에만 남는다.
--   전표 단위 취소 여부를 적을 자리가 없어 여기 둔다.
--   ‼️inventory_txn 에 플래그를 달지 않은 이유: 역분개 이벤트가 이미 상쇄하고 있어
--     원본 행을 건드리면 "그때 실제로 무슨 일이 있었나"가 훼손된다.

CREATE TABLE voucher_cancel
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    ref_no      VARCHAR(30)  NOT NULL COMMENT '취소한 전표번호(P-…폐기 / I-…입고)',
    voucher_kind VARCHAR(20) NOT NULL COMMENT 'DISPOSE / INBOUND',
    reason      VARCHAR(500) NULL COMMENT '취소 사유. 되돌린 이유가 전표보다 중요할 때가 있다',
    reversed    INT          NOT NULL DEFAULT 0 COMMENT '되돌린 재고 이벤트 수(0이면 재고 미관리 상품뿐이었다는 뜻)',
    created_at  DATETIME     NOT NULL,
    created_by  VARCHAR(50)  NULL,
    updated_at  DATETIME     NULL,
    updated_by  VARCHAR(50)  NULL,
    -- 같은 전표를 두 번 취소하면 재고가 반대로 밀린다. DB에서 막는다.
    CONSTRAINT uq_voucher_cancel_ref UNIQUE (ref_no)
) COMMENT '전표 취소 이력(폐기·입고) — 물리 삭제 대신 역분개한 기록';

-- ‼️입고에는 전표번호가 없었다. 폐기는 P-yyyyMMdd-n 이 있는데 입고만 없어
--   "무엇을 되돌릴지" 특정할 수가 없었다.
ALTER TABLE inventory_txn MODIFY COLUMN ref_no VARCHAR(30) NULL
    COMMENT '전표번호. 매출 I- / 폐기 P- / 입고 IN- / 위탁출고 OUT-. 취소·역분개의 단위';

-- 입고 전표번호 채번(IN-yyyyMMdd-n). 폐기(P)·매출(I)과 같은 방식.
INSERT INTO seq_registry (seq_name, seq_val) VALUES ('seq_inbound_no', 0);
