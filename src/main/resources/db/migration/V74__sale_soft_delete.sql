-- 마감 확정 前 거래기록 삭제. 근거: 발주처 회신 2026-08-14 [4] —
--   "마감 확정 前 → 등록담당자가 우클릭 삭제 가능(레거시 수준).
--    마감 확정 後 → 물리삭제 없이 취소 처리"
--
-- ★★물리삭제하지 않는다. 이유가 이 프로젝트의 핵심이다.
--   레거시(`UC_TabPages.vb:543`)는 진짜로 `DELETE salesData where idx=...` 한다.
--   그래도 됐던 건 레거시가 **재고 잔고를 저장하지 않았기** 때문이다 — 행을 지우면
--   매번 다시 계산하는 재고가 저절로 맞았다. 그 구조가 바로 "같은 도서가 화면마다
--   재고가 다르다"는 레거시 최대 결함이었고, 우리는 inventory_txn 단일 진실로 그걸 고쳤다.
--   이벤트 로그에서 행을 지우는 건 원장을 찢는 것과 같다 —
--   재고·채권·이월 스냅샷·월마감·위탁미결이 전부 여기서 파생된다.
--   ‼️레거시조차 작업지시가 나간 건은 안 지운다(`sendData.isDelete=1`, 같은 함수 :529).
--
-- ★취소(canceled)와 삭제(deleted_at)는 **다른 축**이다. 한 플래그로 합치지 말 것.
--     취소 = 있었던 거래를 되돌림 → 장부에 **남아야** 한다(역분개가 사실이다)
--     삭제 = 애초에 잘못 친 것     → 장부에 **남으면 안 된다**(없던 거래다)
--   합치면 마감 후 정당한 반품 취소와 오입력이 섞여 세무 소명 때 가릴 수 없다.

ALTER TABLE sales
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '삭제 시각(마감 前 오입력 정정). 취소(canceled)와 다른 축',
    ADD COLUMN deleted_by VARCHAR(50) NULL COMMENT '삭제자';

ALTER TABLE inventory_txn
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '삭제 시각. 행은 남기고 집계에서만 뺀다 — 단일 진실 유지',
    ADD COLUMN deleted_by VARCHAR(50) NULL COMMENT '삭제자';

-- 유니크 재정의 --------------------------------------------------------------
-- 삭제행이 키를 계속 점유하면 "지웠다가 다시 올리기"가 **조용히** 막힌다.
-- bulk_import_key 는 매출일괄등록 멱등키라, 잘못 들어온 건을 지운 뒤 다시 올리려 하면
-- 임포터가 "이미 처리됨"으로 건너뛴다 — 담당자는 왜 안 들어오는지 알 수 없다.
--   del_key: 활성행 = 고정 sentinel → 활성행끼리만 유일성 강제
--            삭제행 = 삭제시각      → 서로 달라 중복 허용
ALTER TABLE sales
    ADD COLUMN del_key DATETIME(6)
        GENERATED ALWAYS AS (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED;

DROP INDEX ux_sales_bulk_import_key ON sales;
CREATE UNIQUE INDEX ux_sales_bulk_import_key ON sales (bulk_import_key, del_key);

-- 매출번호는 채번이라 재등록 시 새 번호를 받는다 → del_key 불필요.
-- 다만 삭제행이 번호를 점유한 채 남으므로 유니크는 그대로 둔다(번호 재사용을 막는 게 맞다).

-- 활성행 조회 인덱스 ----------------------------------------------------------
CREATE INDEX ix_sales_deleted ON sales (deleted_at);
CREATE INDEX ix_inventory_txn_deleted ON inventory_txn (deleted_at);
