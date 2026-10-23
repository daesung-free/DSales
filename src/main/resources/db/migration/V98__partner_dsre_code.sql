-- 거래처에 **DSRE 거래처코드** 축 추가.
--
-- ★ 무엇이 문제였나 (프론트 실측 2026-10-23 §6③ + 발주처 §3②)
--   우리에게는 거래처를 가리키는 코드가 하나(partners.code = DSLab 매출코드, 'A10A195' 꼴)뿐인데,
--   **학교 마스터와 발주처 양식은 DSRE 코드('10003' 꼴)를 쓴다.**
--
--     DSRE tbl_cust_info :  CUST_CD = '10003'  ·  CITY_NM = '강남'  ·  SUSU = 'A10A195'
--     우리 partners      :  code    = 'A10A195'
--     우리 schools       :  cust_code = '10003'        ← 전부 숫자(4,857/4,860)
--
--   두 코드 체계가 달라 `schools.cust_code = partners.code` 조인이 **한 건도 안 붙었다.**
--   그래서 학교/학원검색의 거래처명1·지역명이 통째로 빈 칸이었다.
--
--   같은 이유로 발주처가 지정한 PDF 파일명도 만들 수 없었다 —
--   "{종류}{DSRE 거래처코드}_{거래처명}_{기간}.pdf, 예) RESULT_10003_강남 하람도서_..."
--   우리가 가진 건 A10A195 라 RESULT_A10A195_… 가 돼 버린다.
--
-- ★ 왜 컬럼을 더하나(별도 매핑표가 아니라)
--   거래처 1건에 DSRE 코드 1개다. 별도 표로 두면 조인이 하나 더 늘고
--   "어느 쪽이 정본인가"라는 규칙이 또 생긴다. 거래처의 속성으로 둔다.
--
-- ★ 값은 비워 두고 시작한다
--   채우는 것은 운영 DSRE2 를 읽어야 하는 일이라 마이그레이션에서 하지 않는다
--   (`POST /masters/clients/sync-dsre-code` — 학교 동기화와 같은 보존형).
--   비어 있으면 예전처럼 빈 칸일 뿐 틀린 값이 나가지는 않는다.

ALTER TABLE partners
    ADD COLUMN dsre_code VARCHAR(20) NULL
        COMMENT 'DSRE2 거래처코드(tbl_cust_info.CUST_CD). 학교 조인·PDF 파일명이 쓴다. 미동기화면 NULL';

-- 학교 조인이 이 컬럼으로 들어온다. UNIQUE 가 아니라 **일반 인덱스**다 —
-- 운영 데이터에 같은 DSRE 코드를 공유하는 거래처가 있는지 확인되지 않았고,
-- UNIQUE 로 걸어 두면 동기화가 통째로 실패한다(중복은 동기화가 보고한다).
CREATE INDEX idx_partners_dsre_code ON partners (dsre_code);
