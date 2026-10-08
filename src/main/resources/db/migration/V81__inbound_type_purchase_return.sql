-- 입고구분에 '매입반품' 추가. 근거: 발주처 1차회신(2026-10-02) #6 —
--   "순매출 매입반품 칸 / 연중 매입반품 실제 발생 여부 → **매입반품 실제 발생**".
--
-- ‼️이전 판단을 뒤집는다. 정본 16p 인터뷰(2026-07-25)는 "매입반품은 연중이 아니라
--   연말에 이감과 일괄 계약(원가×반품개수 단순 차감)"이라 닫혀 있었고, 그래서 칸을 두지 않았다.
--   발주처가 직접 "실제 발생"으로 답했으므로 그 판단은 더 이상 유효하지 않다.
--
-- ★왜 출고가 아니라 입고구분인가 — 매입반품은 **매입의 역(逆)**이다. 사 온 물건을 공급처로
--   되돌려 보내는 것이라 매출·출고 축이 아니다. 같은 INBOUND 축에 두어야 정본 16p의
--   "입고 / 매입반품 / 매입액" 세 칸이 한 축에서 나온다.
--   수량은 **음수로 저장**한다(재고가 실제로 줄어든다). 화면은 양수로 입력하고 서버가 뒤집는다 —
--   폐기와 같은 규칙이다.

ALTER TABLE inventory_txn DROP CHECK ck_inbound_type;

ALTER TABLE inventory_txn
    ADD CONSTRAINT ck_inbound_type CHECK (
        (txn_type = 'INBOUND' AND inbound_type IN ('NORMAL','PURCHASE','RETURN','PURCHASE_RETURN'))
        OR (txn_type <> 'INBOUND' AND inbound_type IS NULL)
    );

ALTER TABLE inventory_txn MODIFY COLUMN inbound_type VARCHAR(20) NULL
    COMMENT '입고구분 NORMAL(정상)/PURCHASE(매입)/RETURN(반품)/PURCHASE_RETURN(매입반품) — INBOUND 이벤트만';
