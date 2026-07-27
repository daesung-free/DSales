-- 매출 성적처리 구분(37p 월별매출액명세서 인원 집계축). 근거: 요구사항 37p + DSRE PROC_YN.
-- 값: GRADED(성적처리)/UNGRADED(비처리). null=비처리로 집계(모의고사 자동 판정 import는 후속).

ALTER TABLE sales
    ADD COLUMN proc_type VARCHAR(20) NULL COMMENT '성적처리 구분 GRADED/UNGRADED (null=비처리)';

-- 월별매출액명세서: 연·월 + 대분류(cat_code 첫 글자) 집계 가속.
CREATE INDEX ix_sales_date_cat ON sales (sales_date, product_id);
