-- 수금 **전표코드**(현장 피드백 2026-10-14 B-5).
--   원문: "수금 여러 건 등록할 때 전표코드로 한 건 생성할 때 묶일 수 있도록"
--
-- 지금은 한 번에 한 건만 받고 수금번호(C-)가 건마다 따로 생긴다.
-- 실무에서는 한 번 입금된 돈을 여러 줄로 쪼개 적는 일이 있고(거래처·항목별),
-- 그 줄들이 "같은 입금"이라는 사실이 어디에도 남지 않았다.
--
-- ★수금번호(C-)는 그대로 둔다 — 줄마다 따로 있어야 개별 수정·삭제가 된다.
--   묶음은 **별도 코드**로 둔다. 둘을 합치면 한쪽 기능이 죽는다.
ALTER TABLE collection
    ADD COLUMN voucher_no VARCHAR(30) NULL COMMENT '전표코드 — 한 번에 등록한 수금 묶음(V-yyyyMMdd-n)';

CREATE INDEX ix_collections_voucher ON collection (voucher_no);

INSERT INTO seq_registry (seq_name, seq_val) VALUES ('seq_collection_voucher_no', 0);
