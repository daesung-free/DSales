-- 자재 입출고 구분 — DSRE2 자재입출고관리의 6종을 보존한다.
-- 근거: 9/27 회의 항목 22 "dsre 자재입출고관리 참고" + 실제 코드 FM_LOGI_MatInOut.cs.
--
-- ★txn_type 만으로는 구분이 사라진다.
--   회수(사고처리)와 회수(반품)이 둘 다 RETURN 이고, 폐기와 파손이 둘 다 DISPOSE 다.
--   잔량 계산은 txn_type 이 하고, "무슨 일이었나"는 이 컬럼이 보존한다.
--   합쳐 두면 나중에 나눌 수 없다.
--
-- ‼️도서 거래에는 채우지 않는다(자재 전용 축). 도서는 출고유형(shipment_type)이 그 역할을 한다.
ALTER TABLE inventory_txn
    ADD COLUMN material_io VARCHAR(20) NULL
        COMMENT '자재 입출고 구분 INBOUND(입고)/RECOVER_ACCIDENT(회수-사고처리)/RECOVER_RETURN(회수-반품)/OUTBOUND(출고)/DISPOSE(폐기)/DAMAGE(파손). 자재 거래에만 값이 있다';

CREATE INDEX ix_txn_material_io ON inventory_txn (material_io);
