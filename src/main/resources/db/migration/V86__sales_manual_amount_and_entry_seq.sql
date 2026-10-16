-- 현장 피드백 2026-10-14 반영 둘. 매출 한 테이블에 걸려 있어 한 마이그레이션으로 묶는다.
--
-- ① 금액 수동 입력 (B-3)
--    원문: "정가는 기본, 자동 계산은 되는데 **수동으로 수정해서 등록**할 수 있도록".
--    지금은 공급가액을 서버가 정가×공급률×수량으로만 만든다.
--
--    ★플래그를 따로 두는 이유 — 금액만 저장하면 "계산값과 우연히 같은 건"과
--      "담당자가 손으로 맞춘 건"을 나중에 가릴 수 없다. 세무 소명 때 묻는 것이 그 구분이다.
--      화면도 이 값으로 「손으로 고친 금액」을 표시한다.
--
-- ② 거래 순번 (B-4)
--    원문: "여러 매출을 등록했을 때 **등록하는 순서로 순번** 생성 — 수정할 때 필요".
--    매출번호(I-…)는 줄마다 따로 채번돼 "한 번에 올린 묶음"을 가리키지 못한다.
--
--    ★묶음번호(entry_no)와 그 안의 순번(entry_seq)을 함께 둔다.
--      순번만 두면 어느 등록의 3번인지 모르고, 묶음만 두면 입력 순서가 사라진다.
--    ‼️기존 매출은 NULL 이다. 소급해 채우지 않는다 — 과거에는 '한 번의 등록'이라는
--      묶음 자체가 기록되지 않았고, 날짜·거래처로 추측해 묶으면 없던 사실을 만드는 것이다.

ALTER TABLE sales
    ADD COLUMN amount_overridden BOOLEAN NOT NULL DEFAULT FALSE
        COMMENT '공급가액을 담당자가 직접 입력했나(계산값 대신)',
    ADD COLUMN entry_no  VARCHAR(30) NULL COMMENT '등록 묶음번호 — 한 번의 등록(E-yyyyMMdd-n)',
    ADD COLUMN entry_seq INT         NULL COMMENT '그 묶음 안의 등록 순번(1부터)';

CREATE INDEX ix_sales_entry_no ON sales (entry_no, entry_seq);

-- 묶음번호 채번. 매출번호(I-)와 **별개 시퀀스**다 —
-- 한 묶음에 줄이 여럿이라 번호 증가 속도가 다르고, 섞으면 둘 다 의미를 잃는다.
INSERT INTO seq_registry (seq_name, seq_val) VALUES ('seq_sales_entry_no', 0);
