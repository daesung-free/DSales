-- 매출 행의 **거래분류**(레거시 salesData.tradeClass).
--
-- 근거: 프론트 실측 2026-10-22 ② — 작업결과의 수량·출고창고가 비는 건이 2,219 중 1,878건.
--   원인은 발송의 거래분류를 **상품의 세부구분**과 맞추고 있던 것. 둘은 다른 축이다 —
--   발송 한 건에 여러 세부구분 상품이 섞여 들어간다.
--     발송 기타고사 → 상품 D모의고사 65,081건 / 발송 대성교재 → 상품 기타고사 41,457건
--   게다가 '대성교재'라는 세부구분은 상품에 아예 없다(발송 833건이 그 값).
--
-- ★레거시는 **매출 행 자체의 거래분류**로 잇는다(작업결과.vb:115) —
--     left(SD.tradeDate,8)=left(AllList.tradeDate,8) AND SD.tradeSeq=AllList.tradeSeq
--     AND SD.custCode=AllList.custCode AND SD.tradeClass='교재'
--   매출에 이 칸이 없어서 상품 쪽 값으로 대신 맞추고 있었다.
--   바꾼 뒤 2026년 기준 341 → 2,215 / 2,221 이 붙는다(전기간 42,254/46,236).
--
-- ‼️tradeSeq 는 넣지 않는다. 이관 원본(salesData 추출 TSV)에 그 칸이 **없다** —
--   넣으려면 레거시 SQL Server 재추출이 필요하다. 거래분류만으로 99.7% 가 붙어
--   지금은 불필요하다고 판단했다. 모자라면 그때 재추출한다.
--   (발송 쪽에는 trade_seq 가 이미 있다.)
--
-- ★★이 DDL 은 **검증을 위해 개발 DB 에 먼저 손으로 적용했다.** 그대로 두면 거기서만
--   Flyway 가 "컬럼 이미 있음"으로 깨진다. 있으면 건너뛰도록 조건을 건다 —
--   예전에 Flyway 이력을 손으로 넣었다가 앱이 기동 불가로 돌아간 적이 있다. 반복하지 않는다.
SET @c := (SELECT COUNT(*) FROM information_schema.columns
            WHERE table_schema = DATABASE() AND table_name = 'sales'
              AND column_name = 'trade_class');
SET @s := IF(@c = 0,
             'ALTER TABLE sales ADD COLUMN trade_class VARCHAR(20) NULL COMMENT ''거래분류(레거시 salesData.tradeClass) — 발송과 잇는 키''',
             'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;

-- 작업결과·작업요청서가 (거래일자, 거래처, 거래분류)로 훑는다.
SET @c := (SELECT COUNT(*) FROM information_schema.statistics
            WHERE table_schema = DATABASE() AND table_name = 'sales'
              AND index_name = 'ix_sales_trade_class');
SET @s := IF(@c = 0,
             'CREATE INDEX ix_sales_trade_class ON sales (sales_date, partner_id, trade_class)',
             'DO 0');
PREPARE st FROM @s; EXECUTE st; DEALLOCATE PREPARE st;
