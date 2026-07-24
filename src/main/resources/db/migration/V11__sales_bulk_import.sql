-- 매출일괄등록(DSRE) 멱등 dedup. DSRE 소스키(req_cd:lst_cd:dtl_cd:req_gn)로 중복 등록 방지.
-- 레거시는 DSRE state='T' write-back에만 의존(분산트랜잭션 아님 → 재실행 시 중복 위험).
-- 우리는 소스키 UNIQUE로 방어: write-back 실패해도 재실행 시 이 키로 스킵.
ALTER TABLE sales ADD COLUMN bulk_import_key VARCHAR(60);
-- MySQL 유니크 인덱스는 NULL을 서로 다르게 취급 → 다중 NULL 허용(부분 인덱스 WHERE 불필요).
CREATE UNIQUE INDEX ux_sales_bulk_import_key ON sales (bulk_import_key);
