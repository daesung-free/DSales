-- 매출·출고에 포장구분 추가. 근거: 레거시 distData.packType(개별1/개별2/반별) + IC회차별작업현황.vb.
--
-- IC회차별작업현황 화면은 분류×도서×회차를 행으로, 포장구분 3종을 열로 펼쳐 수량을 보여준다.
-- 이 축이 없으면 화면 자체가 성립하지 않는다(개별1/개별2/반별 칸을 채울 값이 없음).
--
-- 같은 개념이 세 곳에 있다 — 하나의 축으로 본다:
--   레거시 distData.packType : 개별1 / 개별2 / 반별
--   정본 36p 물류비용등록 작업구분 : 개별봉투 / 개별봉투(SET) / 반별봉투
--   DSRE2 tbl_logis_cost.PACKTYPE : 1 / 3(SET) / 2
-- (V27에서 bom_items에 넣은 pack_type과 같은 축이다.)

ALTER TABLE sales ADD COLUMN pack_type VARCHAR(20) NULL
    COMMENT '포장구분 INDIVIDUAL_1(개별1)/INDIVIDUAL_2(개별2,SET)/CLASS_BUNDLE(반별). 물류 작업현황 집계축';

CREATE INDEX ix_sales_pack_type ON sales (pack_type);
-- 회차별 작업현황 집계(기간 + 회차 + 포장구분) 전용 인덱스
CREATE INDEX ix_sales_round_work ON sales (sales_date, book_round, pack_type);
