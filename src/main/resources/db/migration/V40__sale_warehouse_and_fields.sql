-- 요구사항 대비 누락 필드 보강(38화면 재대조 2026-08-19).
--
-- ★1. sales.warehouse_id — 매출이 어느 창고에서 나갔는지
--    지금까지 매출등록에서 warehouseId를 받아 재고만 차감하고 버렸다.
--    그래서 "이 매출이 어느 창고에서 나갔나"를 되짚을 수 없었고,
--    7p 출고/반품조회의 '창고(재고위치)' 컬럼과 27p 작업결과의 '출고창고' 필터가 둘 다 막혀 있었다.
--    발주처 회신(3-2 나)도 "작업결과 화면 출고창고는 본사물류창고 기본값, 관리자만 전체 선택"을 요구한다.
--    ‼️기존 행은 채울 수 없다(어느 창고였는지 기록이 없다) → nullable.
--
-- ★2. warehouses.use_yn / memo — 31p 창고관리 데이터 항목에 있는데 없었다.
--    사용여부가 없으면 안 쓰는 창고를 목록에서 뺄 방법이 삭제뿐인데,
--    삭제하면 과거 재고 이벤트가 가리키는 창고가 사라진다.
--
-- ★3. inventory_txn.logis_cost_target — 8p 입고/대체등록 '물류작업비여부'.
--    이 입고분이 물류 작업비 청구 대상인지 표시한다(정본 8p 데이터 항목).

ALTER TABLE sales
    ADD COLUMN warehouse_id BIGINT NULL
        COMMENT '출고 창고(7p 재고위치 · 27p 출고창고). 위탁정산 매출은 위탁창고에서 나간다',
    ADD CONSTRAINT fk_sales_warehouse FOREIGN KEY (warehouse_id) REFERENCES warehouses (id);

CREATE INDEX ix_sales_warehouse ON sales (warehouse_id, sales_date);

ALTER TABLE warehouses
    ADD COLUMN use_yn BOOLEAN NOT NULL DEFAULT TRUE COMMENT '사용여부(31p). false=목록에서 숨김',
    ADD COLUMN memo VARCHAR(500) NULL COMMENT '비고(31p)';

ALTER TABLE inventory_txn
    ADD COLUMN logis_cost_target BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '물류작업비 대상 여부(8p). 입고 이벤트에서 사용';
