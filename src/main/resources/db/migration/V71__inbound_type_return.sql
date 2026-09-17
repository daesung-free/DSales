-- 입고구분에 '반품입고' 추가. 근거: 테스트버전 피드백 1차(2026-09-17) 화면5 —
--   "입고구분 드롭다운에 '정상입고'/'매입입고' 2종만 있고 **반품입고 옵션 자체가 없음**
--    (7/30 메일 확정사항 미반영)"
--
-- ★반품입고 자체는 이미 있었다 — 경로가 달랐을 뿐이다.
--   물류 반품입고는 `POST /sales/return-inbound`(29p·28p 진입점)로 만들어 두었고,
--   그건 매출 반품 라인까지 한 트랜잭션으로 만든다. 여기서 더하는 건 **입고구분 값**이다:
--   입고 화면에서 "이 입고는 반품으로 들어온 것"이라고 표시할 수 있어야 한다는 요청.
--
-- ‼️매입액 집계는 건드리지 않는다.
--   순매출조회(16p)의 매입액은 `inbound_type='PURCHASE'` 만 센다(2026-07-28 재무팀 정정).
--   RETURN 이 늘어도 그 집계에 끼어들면 안 된다 — 반품으로 들어온 물건은 매입이 아니다.

ALTER TABLE inventory_txn DROP CHECK ck_inbound_type;

ALTER TABLE inventory_txn
    ADD CONSTRAINT ck_inbound_type CHECK (
        (txn_type = 'INBOUND' AND inbound_type IN ('NORMAL','PURCHASE','RETURN'))
        OR (txn_type <> 'INBOUND' AND inbound_type IS NULL)
    );

ALTER TABLE inventory_txn MODIFY COLUMN inbound_type VARCHAR(20) NULL
    COMMENT '입고구분 NORMAL(정상)/PURCHASE(매입)/RETURN(반품) — INBOUND 이벤트만';
