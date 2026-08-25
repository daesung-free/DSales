-- 사용자 권한 매트릭스. 근거: 발주처 회신 2026-08-21 ①
-- + 드라이브 「사용자권한_구조_설계_예시(수정)」(2026-08-18).
--
-- ★핵심 요구는 "관리자가 운영 중에 바꿀 수 있어야 한다"이다
--   원문: "2단계(화면 단위)·3단계(사용자 개별 필드)는 모두 관리자 ID의 '사용자/권한 관리'
--          화면에서 변경 가능하도록 구현 요청드립니다. 즉 화면/필드 단위 권한을
--          관리자가 운영 중 자유롭게 조정할 수 있는 구조가 필요합니다."
--   지금은 경로별 역할이 SecurityConfig 코드에 박혀 있어 배포 없이는 못 바꾼다. 데이터로 옮긴다.
--
-- ★3단 구조 중 판정에 쓰는 것은 2단계 하나다
--   1단계(역할×메뉴그룹 ○/◐/–)는 <b>초기값을 만드는 규칙</b>일 뿐이다.
--   1·2단계를 둘 다 판정에 쓰면 "그룹은 ◐인데 화면은 Y"일 때 어느 쪽이 이기는지
--   규칙이 하나 더 생긴다. 1단계로 화면 권한을 시딩하고, 판정은 화면 권한만 본다.
--
-- ★조회전용 역할(VIEWER)은 없앤다
--   원문: "조회전용 역할은 제외했습니다 — 3단계 구조 자체가 특정 사용자에게 특정 화면의
--          조회 권한만 개별로 부여할 수 있어, 별도 역할로 두지 않아도 동일한 효과를 낼 수 있습니다."

-- 1) 화면 마스터 --------------------------------------------------------------
--    경로 패턴으로 요청을 화면에 붙인다. 패턴이 겹치면 sort_order가 작은 것이 먼저 걸린다
--    (예: /stock/ledger 가 /stock 보다 앞서야 수불부가 입고등록 권한을 따라가지 않는다).
CREATE TABLE menu_screen
(
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    code         VARCHAR(40)  NOT NULL COMMENT '화면 코드',
    name         VARCHAR(60)  NOT NULL COMMENT '화면명',
    menu_group   VARCHAR(20)  NOT NULL COMMENT '메뉴그룹(ORDER/SALES/CLOSING/LOGISTICS/MASTER)',
    path_pattern VARCHAR(200) NOT NULL COMMENT 'Ant 경로 패턴(/api/v1 이후). 이 화면에 속하는 요청',
    sort_order   INT          NOT NULL DEFAULT 100 COMMENT '작을수록 먼저 매칭 — 구체적인 패턴을 앞에',
    created_at   DATETIME     NOT NULL,
    CONSTRAINT uq_menu_screen_code UNIQUE (code)
) COMMENT '화면 마스터 — 요청 경로를 화면에 붙이는 표';

-- 2) 역할 × 화면 권한 ---------------------------------------------------------
CREATE TABLE role_screen_permission
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    role       VARCHAR(20) NOT NULL COMMENT 'ADMIN/SALES/LOGISTICS/FINANCE',
    screen_id  BIGINT      NOT NULL,
    permission VARCHAR(10) NOT NULL COMMENT 'NONE(비노출)/READ(조회만)/WRITE(조회·등록·수정)',
    updated_at DATETIME    NULL,
    updated_by VARCHAR(50) NULL COMMENT '누가 바꿨는지 — 권한이 언제 왜 열렸나가 감사 질문이다',
    CONSTRAINT uq_role_screen UNIQUE (role, screen_id),
    CONSTRAINT fk_rsp_screen FOREIGN KEY (screen_id) REFERENCES menu_screen (id)
) COMMENT '역할별 화면 권한(2단계) — 관리자가 운영 중 바꾼다';

-- 3) 사용자 개별 권한 플래그 --------------------------------------------------
--    원문: "④·⑤ 항목처럼 특정 담당자에게만 부여하는 권한은, 역할이 아니라
--           사용자 ID 단위 Y/N 필드로 관리자가 직접 지정."
CREATE TABLE user_permission_flag
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id    BIGINT      NOT NULL,
    flag_key   VARCHAR(40) NOT NULL COMMENT '권한 키(PERIOD_LOCK=마감확정, PERIOD_UNLOCK=마감해제)',
    granted    BOOLEAN     NOT NULL DEFAULT FALSE,
    updated_at DATETIME    NULL,
    updated_by VARCHAR(50) NULL,
    CONSTRAINT uq_user_flag UNIQUE (user_id, flag_key),
    CONSTRAINT fk_upf_user FOREIGN KEY (user_id) REFERENCES app_user (id)
) COMMENT '사용자 개별 권한(3단계) — 역할과 무관하게 담당자 지정';

-- 화면 시딩 -------------------------------------------------------------------
-- 패턴이 겹치는 곳은 sort_order로 순서를 준다. 구체적인 것이 앞이다.
INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at) VALUES
  ('STOCK_LEDGER',   '제품수불부현황',   'ORDER',     '/stock/ledger/**',        10, NOW()),
  ('ORDER_SHIP',     '주문조회/출고등록', 'ORDER',     '/orders/**',              20, NOW()),
  ('STOCK_INOUT',    '입고/대체등록',    'ORDER',     '/stock/**',               30, NOW()),
  ('DISPOSAL',       '폐기등록',        'ORDER',     '/disposals/**',           40, NOW()),

  ('SALES_BULK',     '매출일괄등록',     'SALES',     '/sales/bulk/**',          50, NOW()),
  ('SALES',          '매출/출고반품조회', 'SALES',     '/sales/**',               60, NOW()),
  ('CONSIGNMENT',    '위탁정산',        'SALES',     '/consignment/**',         70, NOW()),
  ('DASHBOARD',      '대시보드',        'SALES',     '/dashboard/**',           80, NOW()),

  ('CLOSING',        '마감/채권/세무',   'CLOSING',   '/closing/**',             90, NOW()),

  ('LOGIS_COST',     '물류작업비',      'LOGISTICS', '/logistics-costs/**',    100, NOW()),
  ('LOGIS_WORK',     '작업요청서/결과',  'LOGISTICS', '/logistics/**',          110, NOW()),

  ('MASTER',         '기초관리',        'MASTER',    '/masters/**',            120, NOW()),

  ('AUDIT',          '변경이력',        'MASTER',    '/audit/**',              130, NOW()),
  ('BATCH',          '배치실행',        'MASTER',    '/batch/**',              140, NOW()),
  ('NOTIFICATION',   '알림',           'MASTER',    '/notifications/**',      150, NOW());

-- 역할별 초기 권한(1단계 매트릭스 → 2단계 시딩) ------------------------------
--   관리자: 전부 WRITE
--   영업  : 주문출고 ○ / 매출 ○ / 마감 ◐ / 물류 ◐ / 기초 ◐
--           단 2단계 표에서 입고·폐기는 N(관리자만) — 그대로 반영
--   물류  : 주문출고 그룹은 수불부만 / 물류작업 ○ / 기초 ◐
--   재무  : 매출 ◐ / 마감 ○ / 기초 ◐ / 주문출고·물류 비노출
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT 'ADMIN', id, 'WRITE', NOW() FROM menu_screen;

INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT 'SALES', id,
       CASE code
         WHEN 'STOCK_INOUT' THEN 'NONE'   -- 2단계 표: 입고/대체등록 영업 N
         WHEN 'DISPOSAL'    THEN 'NONE'   -- 2단계 표: 폐기등록 영업 N
         WHEN 'CLOSING'     THEN 'READ'
         WHEN 'LOGIS_COST'  THEN 'READ'
         WHEN 'LOGIS_WORK'  THEN 'READ'
         WHEN 'MASTER'      THEN 'READ'
         WHEN 'AUDIT'       THEN 'READ'
         WHEN 'BATCH'       THEN 'NONE'
         ELSE 'WRITE'
       END, NOW() FROM menu_screen;

INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT 'LOGISTICS', id,
       CASE code
         WHEN 'STOCK_LEDGER' THEN 'READ'   -- 주문출고 그룹 중 재고 확인용만
         WHEN 'LOGIS_COST'   THEN 'WRITE'
         WHEN 'LOGIS_WORK'   THEN 'WRITE'
         WHEN 'STOCK_INOUT'  THEN 'WRITE'  -- 29p 입고/대체등록(물류)
         WHEN 'DISPOSAL'     THEN 'WRITE'
         WHEN 'MASTER'       THEN 'READ'
         WHEN 'NOTIFICATION' THEN 'WRITE'
         ELSE 'NONE'
       END, NOW() FROM menu_screen;

INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT 'FINANCE', id,
       CASE code
         WHEN 'CLOSING'      THEN 'WRITE'
         WHEN 'SALES'        THEN 'READ'   -- 재무×매출관리 = ◐(조회만). 편집은 관리자 계정으로
         WHEN 'SALES_BULK'   THEN 'READ'
         WHEN 'CONSIGNMENT'  THEN 'READ'
         WHEN 'DASHBOARD'    THEN 'READ'
         WHEN 'MASTER'       THEN 'READ'
         WHEN 'AUDIT'        THEN 'READ'
         WHEN 'BATCH'        THEN 'WRITE'  -- 담보만기 알림은 재무 요구사항(25p)
         WHEN 'NOTIFICATION' THEN 'WRITE'
         ELSE 'NONE'
       END, NOW() FROM menu_screen;

-- 기존 VIEWER 계정은 영업 조회 성격에 가장 가깝다. 역할이 사라지므로 옮겨 둔다.
UPDATE app_user SET role = 'SALES' WHERE role = 'VIEWER';
