-- 정상출고 재고연동: 출고/반품(OUTBOUND/RETURN) 이벤트에 출고유형 태그.
-- 제품수불부가 물류 이벤트만으로 매출/무상/교사용/반품 버킷을 자체 분해(스펙 §2-5 수불부 컬럼 대응).
-- 입고/이고/BOM/폐기 이벤트는 NULL(출고유형 무관).
ALTER TABLE inventory_txn ADD COLUMN shipment_type VARCHAR(20)
    CHECK (shipment_type IN ('NORMAL_SHIP','CONSIGN_SHIP','GIFT','TEACHER_USE','RETURN','CANCEL'));
