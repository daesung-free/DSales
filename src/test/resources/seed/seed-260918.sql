-- ════════════════════════════════════════════════════════════════
-- 시험용 시드 — 자재 마스터 · 상품별 목표 · 응시현황(모의고사 매출)
-- 2026-09-18 · 대상: 개발서버 sales DB
-- ════════════════════════════════════════════════════════════════
--
-- 화면이 비어 보이던 3곳을 채운다(프론트 요청).
--   · 자재 마스터 0건       → 회차별 자재 등록·수불부 자재상세가 빈 표
--   · 상품별 목표 전 월 0   → 대시보드 「제품별 목표 달성」 카드가 빔
--   · 응시현황(기간별) 0행  → 표 구조만 확인 가능
-- 덧붙여 학교코드·회차가 들어간 매출을 넣어 통합매출조회의 그 두 칸도 검증 가능하게 한다.
--
-- ★★ 실사용 전에 반드시 지울 것. 맨 아래 「되돌리기」 블록 하나만 돌리면 된다.
--    모든 행의 코드가 'SEED-' 로 시작하고 메모가 '[SEED]' 라 실데이터와 섞이지 않는다.
--
-- ‼️더프 원장·매출일괄등록 대상은 여기 없다 — 그건 DSRE2 복제본을 바꾸는 일이라
--   별도 승인 대상이다(백엔드-남은작업 B-4).
--
-- ‼️재고(inventory·inventory_txn)는 건드리지 않는다.
--   조회 화면 검증이 목적이라 매출만 넣는다. 그래서 제품수불부에는 이 매출이
--   "입고 없이 팔린 것"으로 보인다(재고 음수). 발주처 사양상 정상 표시이고,
--   되돌리기로 함께 사라진다.
--
-- ════════════════════════════════════════════════════════════════
-- ‼️‼️ 돌리기 전에 두 가지 ‼️‼️  (둘 다 실제로 사고가 난 자리다)
-- ════════════════════════════════════════════════════════════════
--
-- ① 어느 DB에 붙어 있는지 먼저 볼 것 — 아래 0번 쿼리 한 줄.
--    기대값은 MySQL 8.x / sales 다.
--    ‼️"MariaDB"가 나오면 **DSRE2** 다. 거긴 매출프로그램 테이블이 아예 없고
--      애초에 우리가 DELETE 를 날릴 DB 가 아니다 — 즉시 멈추고 연결을 바꿀 것.
--      (DBeaver 연결 이름을 sales / dsre2 로 구분해 두면 이 실수가 안 난다.)
--
-- ② 「스크립트 실행」으로 돌릴 것 — DBeaver 단축키 <Alt+X> (Mac ⌥X).
--    <Ctrl+Enter>(문장 실행)로 전체를 선택해 누르면 이 파일이 통째로 한 문장으로
--    전송돼 두 번째 DELETE 에서 1064 로 튕긴다.
--    → 그 경우 **아무것도 실행되지 않으니** DB 는 그대로다. Alt+X 로 다시 돌리면 된다.
-- ════════════════════════════════════════════════════════════════


-- ── 0) 접속 확인 — 여기부터 보고 시작한다 ────────────────────────
-- 기대: 서버버전 8.x  ·  접속DB sales
SELECT VERSION() AS 서버버전, DATABASE() AS 접속DB;


-- ── 1) 안전장치 — 이미 넣었으면 먼저 지우고 시작 ──────────────────
DELETE FROM sales          WHERE memo LIKE '[SEED]%';
DELETE FROM sales_target   WHERE product_id IN (SELECT id FROM products WHERE code LIKE 'SEED-%');
DELETE FROM material_bom   WHERE material_id IN (SELECT id FROM materials WHERE code LIKE 'SEED-%');
DELETE FROM materials      WHERE code LIKE 'SEED-%';
DELETE FROM products       WHERE code LIKE 'SEED-%';
DELETE FROM schools        WHERE school_code LIKE 'SEED-%';
DELETE FROM partners       WHERE code LIKE 'SEED-%';


-- ── 2) 자재 마스터 (자재구분 6종 전부) ───────────────────────────
INSERT INTO materials (code, name, material_type, use_yn, memo, created_at, created_by)
VALUES
 ('SEED-M01', '2026 D.ARCHIVE 국어 시즌1_01회 시험지', 'EXAM_PAPER',   TRUE, '[SEED] 시험용', NOW(), 'seed'),
 ('SEED-M02', '2026 D.ARCHIVE 국어 시즌1_01회 해설지', 'ANSWER_SHEET', TRUE, '[SEED] 시험용', NOW(), 'seed'),
 ('SEED-M03', '공용 OMR 카드',                          'OMR',          TRUE, '[SEED] 시험용', NOW(), 'seed'),
 ('SEED-M04', '교사용 라벨',                            'LABEL',        TRUE, '[SEED] 시험용', NOW(), 'seed'),
 ('SEED-M05', '해설강의 쿠폰(단행본)',                   'BOOK',         TRUE, '[SEED] 시험용', NOW(), 'seed'),
 ('SEED-M06', '포장 부자재',                            'ETC',          TRUE, '[SEED] 시험용', NOW(), 'seed');


-- ── 3) 거래처 · 학교 (매출이 붙을 곳) ────────────────────────────
-- ‼️region 을 채운다 — 응시현황 조회구분 '지역별'이 partners.region 으로 묶는다.
--   비우면 지역별을 골라도 한 덩어리로만 나와 구분이 도는지 확인이 안 된다.
INSERT INTO partners (code, name, region, city_name, type, created_at, created_by)
VALUES ('SEED-P01', '[SEED] 시험용특약점', '서울', '서울', 'NORMAL', NOW(), 'seed');

INSERT INTO schools (school_code, cust_code, cust_name, city, region, school_name,
                     is_school, school_type, source, active, created_at, created_by)
VALUES
 ('SEED-S01', 'SEED-P01', '[SEED] 시험용특약점', '서울', '서울', '[SEED] 시험용고등학교',
  TRUE,  'SCHOOL', 'MANUAL', TRUE, NOW(), 'seed'),
 ('SEED-S02', 'SEED-P01', '[SEED] 시험용특약점', '서울', '서울', '[SEED] 시험용학원',
  FALSE, 'HAKWON', 'MANUAL', TRUE, NOW(), 'seed');


-- ── 4) 상품 ──────────────────────────────────────────────────────
-- ‼️두 가지를 맞춰야 응시현황에 뜬다. 하나라도 어긋나면 빈 표가 그대로다.
--   ① 분류코드가 M + A/B/C 계열 — 더프 판별 규칙(레거시 `고사별처리인원.vb`).
--   ② sales_division 이 sales_divisions 에 있는 코드 — 집계가 그 표와 INNER JOIN 한다.
--      기본 적재된 코드를 쓴다(모의고사·교재·D모의고사·학원콘텐츠·D:VOCA·지자체·기타).
INSERT INTO products (code, name, content_type, price, supply_rate, cat_code, cat_name,
                      sales_division, grade, tax_free, use_yn, created_at, created_by)
VALUES
 ('SEED-B01', '[SEED] 2026 더프 모의고사 1회', 'SELF', 12000, 70, 'M2026A01', '[SEED]모의고사', '모의고사', '고3', FALSE, TRUE, NOW(), 'seed'),
 ('SEED-B02', '[SEED] 2026 더프 모의고사 2회', 'SELF', 12000, 70, 'M2026A02', '[SEED]모의고사', '모의고사', '고3', FALSE, TRUE, NOW(), 'seed'),
 ('SEED-B03', '[SEED] 2026 국어 기본서',       'SELF', 18000, 75, 'H2026A01', '[SEED]교재',     '교재',     '고2', FALSE, TRUE, NOW(), 'seed');


-- ── 5) 세트 자재 매칭 (수불부 자재상세용) ────────────────────────
-- 1회 상품에 회차 전용 자재 2종 + 공통 자재 1종.
INSERT INTO material_bom (set_product_id, round_product_id, material_id, qty_per_set, per_round, created_at, created_by)
SELECT p.id, p.id, m.id, 1, FALSE, NOW(), 'seed'
  FROM products p, materials m
 WHERE p.code = 'SEED-B01' AND m.code IN ('SEED-M01', 'SEED-M02');

INSERT INTO material_bom (set_product_id, round_product_id, material_id, qty_per_set, per_round, created_at, created_by)
SELECT p.id, NULL, m.id, 2, TRUE, NOW(), 'seed'
  FROM products p, materials m
 WHERE p.code = 'SEED-B01' AND m.code = 'SEED-M03';


-- ── 6) 상품별 목표 (당해 1~12월) ─────────────────────────────────
INSERT INTO sales_target (fiscal_year, month, product_id, target_amount, scope, scope_key, entry_type,
                          created_at, created_by)
SELECT YEAR(CURDATE()), mm.m, p.id,
       CASE p.code WHEN 'SEED-B01' THEN 3000000 WHEN 'SEED-B02' THEN 2500000 ELSE 4000000 END,
       'PRODUCT', NULL, 'TARGET', NOW(), 'seed'
  FROM products p
  JOIN (SELECT 1 m UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5 UNION SELECT 6
        UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10 UNION SELECT 11 UNION SELECT 12) mm
 WHERE p.code LIKE 'SEED-B%';


-- ── 7) 매출 — 응시현황 + 통합매출조회 학교·회차 칸 검증용 ────────
-- 학교코드·학교명·회차·성적처리구분을 모두 채운다(그 칸들이 비어 검증이 안 되던 부분).
-- 월을 흩어 넣어 응시현황 월별 칸이 여러 개 차게 한다.
INSERT INTO sales (sales_no, sales_date, partner_id, product_id, sales_type, shipment_type,
                   sales_category, unit_price, supply_rate, qty, supply_amount, tax, total_amount,
                   proc_type, school_code, school_name, book_round, memo, created_at, created_by)
SELECT CONCAT('SEED-I-', LPAD(t.n, 3, '0')),
       DATE(CONCAT(YEAR(CURDATE()), '-', LPAD(t.mon, 2, '0'), '-10')),
       (SELECT id FROM partners WHERE code = 'SEED-P01'),
       (SELECT id FROM products WHERE code = t.pcode),
       'NORMAL_SALES', 'NORMAL_SHIP', 'SALE',
       t.price, 70, t.qty, FLOOR(t.price * 70 / 100) * t.qty, 0,
       FLOOR(t.price * 70 / 100) * t.qty,
       t.proc, t.sch,
       CASE t.sch WHEN 'SEED-S01' THEN '[SEED] 시험용고등학교' ELSE '[SEED] 시험용학원' END,
       t.rnd, '[SEED] 시험용 매출', NOW(), 'seed'
  FROM (
        SELECT 1 n, 3  mon, 'SEED-B01' pcode, 12000 price, 120 qty, 'GRADED'   proc, 'SEED-S01' sch, 1 rnd UNION ALL
        SELECT 2,   3,       'SEED-B01',      12000,        80,      NULL,            'SEED-S02',      1 UNION ALL
        SELECT 3,   5,       'SEED-B02',      12000,       150,      'GRADED',        'SEED-S01',      2 UNION ALL
        SELECT 4,   5,       'SEED-B02',      12000,        60,      NULL,            'SEED-S02',      2 UNION ALL
        SELECT 5,   7,       'SEED-B01',      12000,       200,      'GRADED',        'SEED-S01',      1 UNION ALL
        SELECT 6,   9,       'SEED-B02',      12000,        90,      'GRADED',        'SEED-S02',      2 UNION ALL
        SELECT 7,   9,       'SEED-B03',      18000,        40,      NULL,            'SEED-S01',      NULL
       ) t;


-- ── 확인 ─────────────────────────────────────────────────────────
SELECT '자재'   구분, COUNT(*) 건수 FROM materials    WHERE code LIKE 'SEED-%'
UNION ALL SELECT '자재매칭', COUNT(*) FROM material_bom WHERE material_id IN (SELECT id FROM materials WHERE code LIKE 'SEED-%')
UNION ALL SELECT '상품',     COUNT(*) FROM products     WHERE code LIKE 'SEED-%'
UNION ALL SELECT '목표',     COUNT(*) FROM sales_target WHERE product_id IN (SELECT id FROM products WHERE code LIKE 'SEED-%')
UNION ALL SELECT '매출',     COUNT(*) FROM sales        WHERE memo LIKE '[SEED]%';


-- ════════════════════════════════════════════════════════════════
-- 되돌리기 — 실사용 전에 이 블록만 돌리면 전부 사라진다
-- ════════════════════════════════════════════════════════════════
-- DELETE FROM sales          WHERE memo LIKE '[SEED]%';
-- DELETE FROM sales_target   WHERE product_id IN (SELECT id FROM products WHERE code LIKE 'SEED-%');
-- DELETE FROM material_bom   WHERE material_id IN (SELECT id FROM materials WHERE code LIKE 'SEED-%');
-- DELETE FROM materials      WHERE code LIKE 'SEED-%';
-- DELETE FROM products       WHERE code LIKE 'SEED-%';
-- DELETE FROM schools        WHERE school_code LIKE 'SEED-%';
-- DELETE FROM partners       WHERE code LIKE 'SEED-%';
