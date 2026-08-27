-- 위탁정산 임시저장 화면을 권한 매트릭스에 등록.
--
-- ★없으면 /sales/**(매출·출고반품조회, order 60)에 잡힌다.
--   임시저장은 매출등록(13p)의 일부라 위탁정산과 같은 권한을 따라야 한다 —
--   조회 권한만 있는 사람이 초안을 만들거나, 반대로 정산 담당자가 초안을 못 만드는
--   상태가 되면 안 된다. CONSIGNMENT(order 70)보다 앞(65)에 두어 먼저 걸리게 한다.

INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at)
VALUES ('SETTLEMENT_DRAFT', '위탁정산 임시저장', 'SALES', '/sales/settlement/**', 65, NOW());

-- 권한은 위탁정산(CONSIGNMENT)과 동일하게 시작한다 — 같은 화면의 1단계다.
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT rsp.role, (SELECT id FROM menu_screen WHERE code = 'SETTLEMENT_DRAFT'),
       rsp.permission, NOW()
  FROM role_screen_permission rsp
  JOIN menu_screen ms ON ms.id = rsp.screen_id
 WHERE ms.code = 'CONSIGNMENT';
