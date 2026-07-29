-- 거래처명 분리(발주처 요청 2026-07-29): DSRE는 도시명/상호가 분리돼 있으나 매출프로그램은 합쳐진 이름 하나뿐.
-- 기존 name = 거래처명2(합쳐진 풀네임, 유지). 도시명·거래처명1(상호만) 신규 컬럼 추가.
-- DSRE tbl_cust_info의 CITY_NM(도시명)/CUST_NM(상호)/CUST_FNM(풀네임)과 각각 대응.
ALTER TABLE partners
    ADD COLUMN city_name VARCHAR(50)  NULL COMMENT '도시명(예: 진주). DSRE CITY_NM',
    ADD COLUMN name1     VARCHAR(100) NULL COMMENT '거래처명1=상호만(예: 이룸도서). DSRE CUST_NM';
