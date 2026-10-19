-- 수금 금액의 **음수 금지 제약을 푼다**. 근거: 레거시 운영데이터 실측(2026-10-19).
--
-- V5 에서 `coll_amt BIGINT NOT NULL CHECK (coll_amt >= 0)` 으로 막아 뒀다.
-- 그런데 레거시 `AmtData` 에 **음수 수금이 252건 / −3,628,854,307원** 실재한다.
-- 환불·오입력 정정 같은 되돌림 거래다(2013년 초 조정분이 다수).
--
-- ‼️막아 두면 이관에서 그 252건이 **조용히 빠지고**, 수금 합계가
--   342,436,940,868 → 346,065,795,175 로 **36억 많게** 나온다. 미수금 잔액이
--   그만큼 적게 보인다 — 받을 돈을 덜 받은 것처럼 보이는 쪽이라 더 위험하다.
--
-- ★재고 음수 차단을 푼 것과 같은 판단이다(V56). 발주처 원문 —
--   "재고 음수 차단 로직은 적용되면 안됩니다 … 마이너스로 표시되는게 정상입니다."
--   실제 장부에 있는 값을 제약으로 막으면, 막히는 게 아니라 **사라진다**.
--
-- 음수 수금을 **새로 등록**하는 경로는 따로 열지 않았다. 이 제약은 이관된 과거
-- 데이터를 담기 위한 것이고, 화면에서 음수를 넣을 일이 생기면 그때 별도 판단한다.
--
-- ‼️**이름 없는 CHECK 를 이름으로 지우지 말 것.** MySQL 은 이름을 안 주면
--   `<table>_chk_N` 을 **선언 순서대로** 붙인다. V5 의 collection 은
--   coll_type 이 `_chk_1`, coll_amt 가 `_chk_2` 였는데, `_chk_1` 을 금액 제약으로
--   착각해 지웠다가 **입금구분(enum) 제약을 날렸다**(2026-10-19, 배포 DB에서 실제로).
--   그래서 여기서는 ①둘 다 조건부로 정리하고 ②입금구분 제약을 **이름 붙여** 다시 세운다.
--   이후로는 이름으로 안전하게 다룰 수 있다.

-- ① 금액 음수 금지(자동이름 chk_2) 제거 — 있을 때만.
SET @c := (SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE() AND table_name = 'collection'
              AND constraint_type = 'CHECK' AND constraint_name = 'collection_chk_2');
SET @s := IF(@c > 0, 'ALTER TABLE collection DROP CHECK collection_chk_2', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ② 입금구분(자동이름 chk_1) 제거 — 있을 때만. 바로 아래에서 이름 붙여 다시 만든다.
SET @c := (SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE() AND table_name = 'collection'
              AND constraint_type = 'CHECK' AND constraint_name = 'collection_chk_1');
SET @s := IF(@c > 0, 'ALTER TABLE collection DROP CHECK collection_chk_1', 'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- ③ 입금구분 제약을 **이름 붙여** 보장한다. 이건 enum 값을 지키는 제약이라 살아 있어야 한다
--    (오타 하나로 집계에서 통째로 빠지는 칸이다).
SET @c := (SELECT COUNT(*) FROM information_schema.table_constraints
            WHERE constraint_schema = DATABASE() AND table_name = 'collection'
              AND constraint_type = 'CHECK' AND constraint_name = 'ck_collection_coll_type');
SET @s := IF(@c = 0,
            'ALTER TABLE collection ADD CONSTRAINT ck_collection_coll_type CHECK (coll_type IN (''CASH'',''PROMISSORY'',''PREPAY'',''REPLACE''))',
            'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
