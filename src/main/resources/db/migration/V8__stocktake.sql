-- 재고실사(신규, §7G 결손 해소). 레거시 참고구현 없음 — 우리 재고엔진 원칙으로 설계.
-- 실물 카운트 → 캐시(inventory.qty) 대조 → 차이만큼 ADJUST 이벤트 생성(수불부·단일공식 유지).

-- 재고이벤트 ADJUST(재고조정)는 V1 txn_type CHECK에 이미 포함됨(MySQL 이식 시 통합). 여기선 no-op.

-- 실사 헤더
CREATE TABLE stocktake (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    stocktake_no   VARCHAR(30) UNIQUE,          -- ST-yyyyMMdd-seq
    warehouse_id   BIGINT NOT NULL REFERENCES warehouses(id),
    stocktake_date DATE   NOT NULL,
    memo           VARCHAR(1000),
    created_at     TIMESTAMP NOT NULL,
    created_by     VARCHAR(50),
    updated_at     TIMESTAMP,
    updated_by     VARCHAR(50)
);
INSERT INTO seq_registry (seq_name, seq_val) VALUES ('seq_stocktake_no', 0);  -- 실사번호

-- 실사 명세(상품별): 시스템수량(대조 시점 캐시) / 실물수량 / 차이
CREATE TABLE stocktake_line (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    stocktake_id BIGINT  NOT NULL REFERENCES stocktake(id),
    product_id   BIGINT  NOT NULL REFERENCES products(id),
    system_qty   INTEGER NOT NULL,              -- 대조 시점 시스템(캐시) 수량
    counted_qty  INTEGER NOT NULL CHECK (counted_qty >= 0),  -- 실물 수량
    diff         INTEGER NOT NULL,              -- counted - system (조정량)
    created_at   TIMESTAMP NOT NULL,
    created_by   VARCHAR(50),
    updated_at   TIMESTAMP,
    updated_by   VARCHAR(50)
);
CREATE INDEX ix_stocktake_line_st ON stocktake_line (stocktake_id);
