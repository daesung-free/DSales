-- 자재 재고 — 재고 원장에 '자재' 축을 연다.
-- 근거: 9/27 회의 확정사항 A-1(항목 4·5·6·22) —
--   "DSRE는 매출만 변경, 매출프로그램에서 수불·자재 다 관리" / "자재별로 입고 가능하게"
--
-- ★이것이 기존 판단을 뒤집은 지점이다.
--   그동안은 "자재 재고를 우리가 만들면 DSRE2(tbl_materials_info.CNT)와 이중 관리가 된다"는
--   이유로 보류(B-3)했다. 이번 회의에서 발주처가 **매출프로그램 단독 관리**로 정리했으므로
--   보류 사유가 사라졌다. DSRE 자재 재고는 더 이상 우리 기준이 아니다.
--
-- ★왜 별도 테이블(material_inventory)을 만들지 않는가
--   재고는 `inventory_txn` 하나가 유일 진실이고 잔량은 그 합이라는 규칙을 지키기 위해서다.
--   자재도 입고·이고·폐기·실사·조립소모를 똑같이 겪는다. 표를 나누면 그 7종 동작과
--   전표 취소·마감잠금까지 전부 두 벌이 되고, 언젠가 둘이 어긋난다(레거시 #4가 정확히 그것).
--
-- ‼️대신 감수해야 하는 위험 — **기존 리포트에 자재 거래가 섞이는 것**
--   도서 리포트 대부분은 products 를 조인해서 자재 행(product_id IS NULL)이 자연히 빠진다.
--   그러나 products 를 안 거치는 집계(폐기 내역·평균입고원가 등)는 그냥 딸려 들어온다.
--   그래서 조인으로 안 걸러지는 쿼리에는 명시 조건을 넣었고,
--   MaterialStockIsolationIntegrationTest 가 "자재 거래를 넣어도 도서 리포트 숫자가
--   변하지 않는다"를 고정한다. 사람이 매번 기억하는 대신 테스트가 잡게 둔다.

-- 1) 재고 잔량 ------------------------------------------------------------------
ALTER TABLE inventory
    MODIFY COLUMN product_id BIGINT NULL COMMENT '도서(상품). 자재 행이면 NULL',
    ADD COLUMN material_id BIGINT NULL COMMENT '자재. 도서 행이면 NULL';

ALTER TABLE inventory
    ADD CONSTRAINT fk_inventory_material FOREIGN KEY (material_id) REFERENCES materials (id);

-- 한 행은 도서이거나 자재이거나 — 둘 다이거나 둘 다 아닌 행은 만들 수 없다.
ALTER TABLE inventory
    ADD CONSTRAINT ck_inventory_item CHECK (
        (product_id IS NOT NULL AND material_id IS NULL)
     OR (product_id IS NULL AND material_id IS NOT NULL));

-- 자재도 (자재, 창고) 하나뿐이어야 한다. 도서 쪽 UNIQUE(product_id, warehouse_id)는 그대로 두면 된다
-- — product_id 가 NULL 인 자재 행은 MySQL 이 서로 다른 값으로 보아 그 제약에 걸리지 않는다.
CREATE UNIQUE INDEX ux_inventory_material ON inventory (material_id, warehouse_id);

-- 2) 재고 이벤트 ----------------------------------------------------------------
ALTER TABLE inventory_txn
    MODIFY COLUMN product_id BIGINT NULL COMMENT '도서(상품). 자재 거래면 NULL',
    ADD COLUMN material_id BIGINT NULL COMMENT '자재. 도서 거래면 NULL';

ALTER TABLE inventory_txn
    ADD CONSTRAINT fk_txn_material FOREIGN KEY (material_id) REFERENCES materials (id);

ALTER TABLE inventory_txn
    ADD CONSTRAINT ck_txn_item CHECK (
        (product_id IS NOT NULL AND material_id IS NULL)
     OR (product_id IS NULL AND material_id IS NOT NULL));

-- 자재 수불 조회가 (자재, 기간)으로 들어온다.
CREATE INDEX ix_txn_material_date ON inventory_txn (material_id, trade_date);

-- 3) 자재구분 중복 제거 ----------------------------------------------------------
-- ‼️V27 이 bom_items.material_type 을 넣을 당시엔 자재 마스터가 없었다.
--   V57 에서 materials 가 생기면서 같은 뜻이 두 군데가 됐고, 이제 자재 재고까지 붙으면
--   "그 자재의 구분"을 어디서 읽느냐가 화면마다 갈릴 수 있다 — 물류 작업비 단가가
--   자재구분으로 정해지므로 갈리면 금액이 달라진다. 자재 마스터 한 곳으로 모은다.
--
--   ★값을 버리지 않는다: bom_items 에만 있던 자재구분은 매칭되는 자재 마스터가 없으므로
--   지우면 복구가 안 된다. 컬럼을 드롭하기 전에 남아 있는 값이 있는지 확인할 수 있게
--   이번 마이그레이션에서는 **주석 처리만** 하고 드롭은 다음 단계로 미룬다.
ALTER TABLE bom_items
    MODIFY COLUMN material_type VARCHAR(20) NULL
        COMMENT '⚠️사용 중단(V78). 자재구분의 정본은 materials.material_type 이다. 잔여값 확인 후 드롭 예정';
