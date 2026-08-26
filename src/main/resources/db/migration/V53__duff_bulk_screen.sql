-- 매출일괄등록(더프, 14p) 화면을 권한 매트릭스에 등록.
--
-- ★없으면 조용히 엉뚱한 화면의 권한을 따라간다
--   경로 패턴은 sort_order 순으로 먼저 걸리는 것이 이긴다. /sales/duff/** 를 등록하지 않으면
--   /sales/**(매출/출고반품조회, order 60)에 잡혀 "매출조회 권한만 있는 사람이 일괄등록을 실행"
--   하거나 그 반대가 된다. 교재 일괄등록(/sales/bulk/**, order 50)과 같은 성격이므로
--   그 바로 앞(45)에 두고 권한도 같게 준다.
--
-- 근거: 정본 14p(매출일괄등록 - DSRE 연동) + 레거시 매출가져오기.vb.

INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at)
VALUES ('SALES_DUFF', '매출일괄등록(더프)', 'SALES', '/sales/duff/**', 45, NOW());

-- 권한은 교재 일괄등록(SALES_BULK)과 동일하게 시작한다 — 같은 업무, 상품군만 다르다.
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT rsp.role, (SELECT id FROM menu_screen WHERE code = 'SALES_DUFF'),
       rsp.permission, NOW()
  FROM role_screen_permission rsp
  JOIN menu_screen ms ON ms.id = rsp.screen_id
 WHERE ms.code = 'SALES_BULK';
