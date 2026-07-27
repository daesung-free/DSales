-- 기초마스터 확장(요구사항 32p 도서관리 + 창고). 근거: 재무팀 미팅 2026-07.
-- 도서: 매출구분(집계기준)·수불부노출(제품수불부 포함여부)·Web게시여부.
-- 창고: 실물재고여부(위탁창고=N → 수불부 실재고 제외)·소속거래처(위탁창고 1:1).

-- 도서(products)
ALTER TABLE products
    ADD COLUMN sales_division VARCHAR(30) NULL       COMMENT '매출구분(매출액정리·순매출조회 집계기준)',
    ADD COLUMN ledger_visible BOOLEAN NOT NULL DEFAULT TRUE  COMMENT '수불부노출 여부(제품수불부 집계 포함)',
    ADD COLUMN web_visible    BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Web/신청사이트 게시 여부';

-- 창고(warehouses)
ALTER TABLE warehouses
    ADD COLUMN physical_stock  BOOLEAN NOT NULL DEFAULT TRUE COMMENT '실물재고여부(N=가상/위탁 → 수불부 실재고 제외)',
    ADD COLUMN owner_client_id BIGINT NULL                   COMMENT '소속거래처(위탁창고 1:1) → partners.id';

-- 위탁창고는 가상재고 → 실물재고여부 N으로 백필
UPDATE warehouses SET physical_stock = FALSE WHERE type = 'CONSIGN';

ALTER TABLE warehouses
    ADD CONSTRAINT fk_warehouse_owner_client FOREIGN KEY (owner_client_id) REFERENCES partners(id);
