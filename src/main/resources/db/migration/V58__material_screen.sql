-- 자재 마스터 화면을 권한 매트릭스에 등록.
--
-- ★/masters/materials 는 /masters/**(기초관리, order 120)에 이미 걸린다.
--   그래도 별도 화면으로 두는 이유: 자재는 물류가 다루는 데이터인데
--   기초관리 묶음 권한을 그대로 쓰면 물류에게 거래처·도서까지 열어야 한다.
--   화면을 나눠 두면 관리자가 자재만 물류에 열 수 있다.
INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at)
VALUES ('MATERIAL', '자재 마스터', 'MASTER', '/masters/materials/**', 115, NOW());

-- 초기 권한은 기초관리(MASTER)와 동일하게 시작한다. 물류에게 쓰기를 열지는 운영에서 정한다.
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT rsp.role, (SELECT id FROM menu_screen WHERE code = 'MATERIAL'),
       rsp.permission, NOW()
  FROM role_screen_permission rsp
  JOIN menu_screen ms ON ms.id = rsp.screen_id
 WHERE ms.code = 'MASTER';
