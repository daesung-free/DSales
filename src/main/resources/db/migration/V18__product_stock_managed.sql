-- 재고관리 여부. 근거: 레거시 실측 — 모의고사는 재고장부(invenData) 미기록·제품수불부 없음(인원 종량제).
-- 기본 true(기존 상품 동작 무변). 모의고사 등 인원기반 상품은 마스터에서 false로 지정 → 매출 시 재고 미차감.

ALTER TABLE products
    ADD COLUMN stock_managed BOOLEAN NOT NULL DEFAULT TRUE COMMENT '재고관리 여부(false=인원기반, 매출 시 재고 미차감)';
