-- 이고·세트작업 전표번호. 근거: 프론트 실측(2026-09-18) —
--   "이고·세트 전표는 되돌릴 방법이 없습니다. /stock/inbound/{refNo} 하나뿐이고 설명이 '입고 전표'라,
--    화면은 입고 행에만 버튼을 엽니다(추측으로 쏘지 않기 위해)."
--
-- ★원인: 이고(transfer)와 BOM 조립·해체가 만든 재고 이벤트에 **전표번호가 안 붙었다**.
--   `InventoryTxn.transfer(...)`·`.bom(...)`이 refNo 를 아예 세팅하지 않아 NULL 로 남는다.
--   취소·삭제는 전부 refNo 로 대상을 찾으므로(`findAllByRefNo`), 번호가 없으면 **가리킬 수가 없다**.
--   입고(IN-)·폐기(P-)·매출(I-)·위탁(OUT-)엔 번호가 있는데 이 둘만 빠져 있었다.
--
-- ★과거 데이터는 소급 부여하지 않는다.
--   어떤 행들이 한 전표였는지 되살릴 근거가 없다 — (상품·창고·일자)가 같아도 별개 작업일 수 있고,
--   묶는 순간 되돌리기가 남의 작업까지 끌고 간다. 번호가 생긴 이후 건부터 되돌릴 수 있다.
--   화면은 refNo 가 없는 행의 되돌리기 버튼을 비활성으로 두면 된다.

INSERT INTO seq_registry (seq_name, seq_val) VALUES ('seq_transfer_no', 0);   -- 이고 TR-
INSERT INTO seq_registry (seq_name, seq_val) VALUES ('seq_bomwork_no', 0);    -- 세트작업 BW-
