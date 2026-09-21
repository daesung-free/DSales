-- ════════════════════════════════════════════════════════════════
-- 시험용 시드 ② — 매출일괄등록(더프) 화면용
-- 2026-09-21 · 대상: ‼️DSRE2 **복제본**(배포서버 dsre-mariadb 컨테이너)
-- ════════════════════════════════════════════════════════════════
--
-- ‼️‼️ 이 파일만 DB가 다르다 ‼️‼️
--   앞서 돌린 seed-260918.sql = sales(MySQL 8).
--   이 파일        = dsre2(MariaDB 10.2, 배포서버의 dsre-mariadb 컨테이너).
--   두 파일을 서로 바꿔 돌리면 전부 "테이블 없음"으로 튕긴다.
--
-- ‼️‼️ 운영 DSRE2에는 절대 돌리지 말 것 ‼️‼️
--   운영 DSRE2는 재구축 대상이 아니고 **읽기 전용**으로만 쓰기로 돼 있다.
--   반드시 우리가 띄운 **복제본**에만 넣는다. 0번 쿼리로 먼저 확인한다.
--
-- 「스크립트 실행」으로 돌릴 것 — DBeaver <Alt+X> (Mac ⌥X).
--   전체 선택 + <Ctrl+Enter>로 누르면 한 문장으로 전송돼 1064로 튕긴다(아무것도 실행 안 됨).
--
-- ── 이게 왜 필요한가 ────────────────────────────────────────────
-- 매출일괄등록은 우리 DB가 아니라 DSRE2의 신청 데이터를 읽어 매출로 옮기는 화면이다.
-- 복제본에 조건에 맞는 신청건이 없으면 화면이 빈 표로만 보인다.
--
-- ── 매핑이 핵심이다 ─────────────────────────────────────────────
-- 일괄등록은 DSRE 값을 우리 마스터에서 이렇게 찾는다(DuffSalesImportService):
--     거래처 = partners.code      ==  tbl_cust_info.MACHUL_CD   (매출코드)
--     상품   = products.code      ==  tbl_request_info.DTL_CD   (과목코드)
-- 그래서 sales DB 쪽에도 짝이 되는 마스터 2건이 필요하다.
-- → 맨 아래 「sales DB에 함께 넣을 것」 블록을 **sales 연결에서** 따로 돌릴 것.
--   안 넣으면 화면에 행은 뜨지만 전부 UNMAPPED 로만 나온다.
--
-- ‼️상품코드가 'SEED-' 로 시작하지 않는다(DTL_CD가 정수 컬럼이라 '90001' 이어야 한다).
--   지울 때 빠뜨리기 쉬우니 되돌리기 블록을 그대로 쓸 것.
-- ════════════════════════════════════════════════════════════════


-- ── 0) 접속 확인 — 여기부터 보고 시작한다 ────────────────────────
-- 기대: MariaDB 10.2 · dsre2
-- ‼️MySQL 8.x / sales 가 나오면 **DB를 잘못 잡은 것**이다. 즉시 멈출 것.
SELECT VERSION() AS 서버버전, DATABASE() AS 접속DB;


-- ── 1) 안전장치 — 이미 넣었으면 먼저 지우고 시작 ──────────────────
DELETE FROM tbl_request_rtn    WHERE REQ_CD BETWEEN 990001 AND 990099;
DELETE FROM tbl_request_cnt    WHERE REQ_CD BETWEEN 990001 AND 990099;
DELETE FROM tbl_request_info   WHERE REQ_CD BETWEEN 990001 AND 990099;
DELETE FROM tbl_resource_info  WHERE DTL_CD = 90001;
DELETE FROM tbl_mgrcd_cnt      WHERE DTL_CD = '90001';
DELETE FROM tbl_product_amt    WHERE DTL_CD = '90001';
DELETE FROM tbl_product_dtl    WHERE DTL_CD = 90001;
DELETE FROM tbl_school_ref     WHERE MGR_CD IN ('SDS01','SDS02');
DELETE FROM tbl_product_info   WHERE PROD_CD = 'SDP01';
DELETE FROM tbl_school_info    WHERE MGR_CD IN ('SDS01','SDS02');
DELETE FROM tbl_cust_info      WHERE CUST_CD = 'SDC01';


-- ── 2) 지사(거래처) ──────────────────────────────────────────────
-- ★MACHUL_CD 가 우리 partners.code 와 맞아야 매핑된다. char(7)이라 7자를 넘기면 안 된다.
INSERT INTO tbl_cust_info
  (CUST_CD, CUST_NM, CUST_GB, CITY_CD, CITY_NM, CUST_FNM, REG_NO, OWNER_NM,
   SUSU, MACHUL_CD, END_GUBUN, REG_USER)
VALUES
  ('SDC01', '[SEED]시험지사', '특약점', '01', '서울', '[SEED]시험지사(주)',
   '220-81-62517', '김대표', 70.00, 'SEEDP01', 'N', 'seed');


-- ── 3) 학교 ──────────────────────────────────────────────────────
INSERT INTO tbl_school_info (MGR_CD, CITY_CD, SCH_NM, REG_USER)
VALUES ('SDS01', '01', '[SEED]시험용고등학교', 'seed'),
       ('SDS02', '01', '[SEED]시험용여자고등학교', 'seed');


-- ── 4) 상품(분류) · 상품상세(과목) ───────────────────────────────
INSERT INTO tbl_product_info
  (PROD_CD, PROD_NM, YEAR, COM_CD, USE_YN, SALE_YN, MAPPLY, PRODTYPE2, EASY_GN, REG_USER)
VALUES ('SDP01', '[SEED]더프 모의고사', 2026, 'SDC01', 'Y', 'Y', 'N', '모의고사', 'Y', 'seed');

-- ★DTL_CD 를 직접 지정한다(auto_increment 지만 값을 넣으면 그 값이 들어간다).
--   이 숫자가 그대로 우리 products.code 가 돼야 한다 — 그래서 고정값이어야 한다.
INSERT INTO tbl_product_dtl
  (DTL_CD, DTL_NM, PROD_CD, GRADE, PROC_YN, USE_YN, SALE_YN, SALE_DT,
   GEYUL_GN, WEB_FILTER_YN, BOX_GB, EASY_GN, REG_USER)
VALUES
  (90001, '[SEED]더프 1회 국어', 'SDP01', '3', 'Y', 'Y', 'Y', '20261231',
   'N', 'Y', 'N', 'N', 'seed');

-- 단가(처리/비처리 각각). 과목수 1~9 구간 하나로 덮는다.
-- ‼️여기가 비면 정가가 0이 되고, 화면은 ZERO_AMOUNT 로만 나온다.
INSERT INTO tbl_product_amt (DTL_CD, SUBSTCNT, SUBEDCNT, PROC_GN, AMT)
VALUES ('90001', 1, 9, 'Y', 12000),
       ('90001', 1, 9, 'N', 10000);

-- 공급률·청구구분. CHARGE_GN: T=신청 / S=처리 / R=등록
INSERT INTO tbl_school_ref (MGR_CD, PROD_CD, SALE_GN, CHARGE_GN, AMTSUSU, DISSUSU, REG_USER)
VALUES ('SDS01', 'SDP01', 'Y', 'T', 70.00, 0, 'seed'),
       ('SDS02', 'SDP01', 'Y', 'T', 70.00, 0, 'seed');

-- 등록인원(청구구분 R 인 계약이 쓰는 값)
INSERT INTO tbl_mgrcd_cnt (DTL_CD, MGR_CD, CNT) VALUES ('90001', 'SDS01', 35),
       ('90001', 'SDS02', 20);


-- ── 5) 응시 리소스 ───────────────────────────────────────────────
-- ‼️신청인원은 저장함수 FUNC_REQINWON_GET 이 계산하는데, 그 함수가
--   tbl_resource_info 를 RIGHT JOIN 하고 **GEYUL='9'(공통)** 만 먼저 본다.
--   이 행이 없으면 신청 수량을 아무리 넣어도 인원이 0으로 나온다.
INSERT INTO tbl_resource_info
  (RES_CD, DTL_CD, RES_NM, GYOSI, GEYUL, DISP_GN, PRINT_YN, SORTKEY,
   PACKING_GYOSI, EXTRA_GN, BONBU_GN, REG_USER)
VALUES (990001, 90001, '[SEED]국어영역', '1', '9', 'Y', 'N', 1, '1', 'Y', 'Y', 'seed');


-- ── 6) 신청건 3종 ────────────────────────────────────────────────
-- 화면에서 세 갈래가 다 보이도록 성격을 달리 넣는다.
--   990001 비처리(PROC_YN2='N') → 청구인원 = 신청인원 120
--   990002 처리  (PROC_YN2='Y') → 청구인원 = 처리인원 (tbl_request_rtn 에서 옴) 80
--   990003 처리이나 STATE='S'   → '발송완료만' 체크 시 사라지는지 확인용(청구 40명)
--
-- 미리보기 예상 합계(전체 기간 2026-04, 처리구분 '모두'):
--   990001  10,000 × 70% × 120 =   840,000
--   990002  12,000 × 70% ×  80 =   672,000
--   990003  12,000 × 70% ×  40 =   336,000   ← '발송완료만' 체크 시 빠진다
--   ─────────────────────────────────────
--   전체 1,848,000  ·  발송완료만 1,512,000
-- ‼️APPLY_GN='S'(지사 신청)가 아니면 조회 자체가 안 걸린다.
-- ‼️REQ_DATE 는 char(8) 이다 — 날짜형이 아니라 '20260410' 형식 문자열.
INSERT INTO tbl_request_info
  (REQ_CD, DTL_CD, CUST_CD, MGR_CD, APPLY_GN, STATE, REQ_DATE,
   PROC_YN, PROC_YN2, LGS_GN, WORK_CHK, BIGO, REG_USER)
VALUES
  (990001, 90001, 'SDC01', 'SDS01', 'S', 'D', '20260410', 'N', 'N', 'H', 'N', '[SEED]비처리', 'seed'),
  (990002, 90001, 'SDC01', 'SDS01', 'S', 'D', '20260415', 'Y', 'Y', 'H', 'N', '[SEED]처리',   'seed'),
  (990003, 90001, 'SDC01', 'SDS02', 'S', 'S', '20260420', 'Y', 'Y', 'H', 'N', '[SEED]준비중', 'seed');

-- 신청 수량 — FUNC_REQINWON_GET 이 이걸 합산해 신청인원을 만든다.
INSERT INTO tbl_request_cnt (REQ_CD, SEQ, RES_CD, CNT)
VALUES (990001, 1, 990001, 120),
       (990002, 1, 990001, 100),
       (990003, 1, 990001,  50);

-- 처리인원 — 처리건(990002·990003)의 청구 근거.
-- ‼️처리건인데 이 행이 없으면 청구인원이 0이 되어 화면에 ZERO_INWON 으로만 뜬다
--   (비처리건은 신청인원을 쓰므로 필요 없다).
INSERT INTO tbl_request_rtn (REQ_CD, RESCNT, PROCNT)
VALUES (990002, 1, 80),
       (990003, 1, 40);


-- ── 확인 ─────────────────────────────────────────────────────────
-- 신청인원이 0이 아니어야 한다. 0이면 5번(리소스) 또는 저장함수가 없는 것이다.
SELECT REQ_CD 신청번호, REQ_DATE 신청일, PROC_YN2 처리구분, STATE 상태,
       FUNC_REQINWON_GET(REQ_CD) 신청인원
  FROM tbl_request_info
 WHERE REQ_CD BETWEEN 990001 AND 990099
 ORDER BY REQ_CD;


-- ════════════════════════════════════════════════════════════════
-- ★ sales DB에 함께 넣을 것 — ‼️여기부터는 **sales(MySQL) 연결**에서 돌린다
-- ════════════════════════════════════════════════════════════════
-- 이게 없으면 화면에 행은 뜨지만 전부 UNMAPPED 로만 나온다.
-- (거래처코드 SEEDP01 = 위 MACHUL_CD, 도서코드 90001 = 위 DTL_CD)
--
-- DELETE FROM products WHERE code = '90001';
-- DELETE FROM partners WHERE code = 'SEEDP01';
--
-- INSERT INTO partners (code, name, region, city_name, type, biz_no, boss_name, created_at, created_by)
-- VALUES ('SEEDP01', '[SEED]시험지사', '서울', '서울', 'NORMAL', '220-81-62517', '김대표', NOW(), 'seed');
--
-- INSERT INTO products (code, name, content_type, price, supply_rate, cat_code, cat_name,
--                       sales_division, grade, tax_free, use_yn, created_at, created_by)
-- VALUES ('90001', '[SEED]더프 1회 국어', 'SELF', 12000, 70, 'M2026A01', '[SEED]모의고사',
--         '모의고사', '고3', FALSE, TRUE, NOW(), 'seed');


-- ════════════════════════════════════════════════════════════════
-- 되돌리기 — 실사용 전에 반드시 돌릴 것
--   ① dsre2 연결에서 아래를,  ② sales 연결에서 위 sales 블록의 DELETE 3줄을
-- ════════════════════════════════════════════════════════════════
-- DELETE FROM tbl_request_rtn    WHERE REQ_CD BETWEEN 990001 AND 990099;
-- DELETE FROM tbl_request_cnt    WHERE REQ_CD BETWEEN 990001 AND 990099;
-- DELETE FROM tbl_request_info   WHERE REQ_CD BETWEEN 990001 AND 990099;
-- DELETE FROM tbl_resource_info  WHERE DTL_CD = 90001;
-- DELETE FROM tbl_mgrcd_cnt      WHERE DTL_CD = '90001';
-- DELETE FROM tbl_product_amt    WHERE DTL_CD = '90001';
-- DELETE FROM tbl_product_dtl    WHERE DTL_CD = 90001;
-- DELETE FROM tbl_school_ref     WHERE MGR_CD IN ('SDS01','SDS02');
-- DELETE FROM tbl_product_info   WHERE PROD_CD = 'SDP01';
-- DELETE FROM tbl_school_info    WHERE MGR_CD IN ('SDS01','SDS02');
-- DELETE FROM tbl_cust_info      WHERE CUST_CD = 'SDC01';
