-- 입고구분(정상입고/매입입고). 근거: 요구사항정의서 8p 입고/대체등록 [정정 2026-07-28 재무팀].
-- 매입입고(PURCHASE)로 등록된 입고만 16p 순매출조회의 외부콘텐츠 매입 데이터로 자동 연결.
-- 별도 매입 업로드 화면·파서 없이 입고 등록에 구분값 하나로 처리(범위 축소).

ALTER TABLE inventory_txn
    ADD COLUMN inbound_type VARCHAR(20) NULL COMMENT '입고구분 NORMAL/PURCHASE (INBOUND 이벤트만)';

-- 기존 입고 백필: 외부콘텐츠 상품의 입고는 매입입고로 간주(기존 순매출조회 이익률 동작 보존),
-- 그 외 입고는 정상입고. 비-INBOUND 이벤트는 NULL 유지.
UPDATE inventory_txn t
    JOIN products p ON p.id = t.product_id
    SET t.inbound_type = CASE WHEN p.content_type = 'EXTERNAL' THEN 'PURCHASE' ELSE 'NORMAL' END
    WHERE t.txn_type = 'INBOUND';

-- 무결성: INBOUND는 반드시 구분값 보유, 그 외는 NULL
ALTER TABLE inventory_txn
    ADD CONSTRAINT ck_inbound_type CHECK (
        (txn_type = 'INBOUND' AND inbound_type IN ('NORMAL','PURCHASE'))
        OR (txn_type <> 'INBOUND' AND inbound_type IS NULL)
    );
