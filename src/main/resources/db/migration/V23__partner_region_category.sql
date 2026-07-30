-- 통합매출조회(12p) 데이터 완비: 거래처에 지역·거래처구분 추가.
-- 근거: 레거시 통합매출조회 쿼리 — 지역=custData.zone1, 거래처구분=type1(특약점/기타학원/B2B/대성/자사몰).
-- 값 채움: 매출프로그램 직접입력 또는 DSRE tbl_cust_info 동기화(후속, 조건부).
ALTER TABLE partners
    ADD COLUMN region          VARCHAR(50) NULL COMMENT '지역(관할, 레거시 custData.zone1 / DSRE CITY_NM)',
    ADD COLUMN client_category  VARCHAR(30) NULL COMMENT '거래처구분(특약점/기타학원/B2B/대성/자사몰, 레거시 type1)';
