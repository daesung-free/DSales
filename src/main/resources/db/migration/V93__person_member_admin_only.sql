-- 개인회원 조회를 **관리자 전용**으로 좁힌다.
--
-- ‼️`/masters/person-members` 가 기초관리(`/masters/**`, sort 120)에 묻혀 있어
--   영업·물류·재무가 모두 READ 였다(프론트 실측 2026-10-22, 세 계정 모두 200).
--   이 표에는 **학생이름·주소·연락처**가 들어간다(V29: student_name·addr1·addr2·tel1·tel2).
--   발주처는 개인회원관리를 **제외**로 회신했고 사유가 "오래된 개인정보를 보유하는 것 자체가 리스크"였다.
--   지금 0건이지만 열려 있을 이유가 없다 — 담을 자리가 있으면 언젠가 담긴다.
--
-- ★sort_order 는 MASTER(120)보다 **앞서야** 한다 — 매칭은 앞선 패턴이 이긴다.
--   자재 마스터(MATERIAL, 115)가 같은 방식으로 /masters/** 를 앞질러 있다.
INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at)
SELECT 'PERSON_MEMBER', '개인회원관리', 'MASTER', '/masters/person-members/**', 114, NOW()
 WHERE NOT EXISTS (SELECT 1 FROM menu_screen WHERE code = 'PERSON_MEMBER');

-- 관리자만 WRITE, 나머지 역할은 NONE. 역할을 빼먹으면 상위 패턴(MASTER)으로 떨어지지 않고
-- '권한 행 없음'이 되므로, 네 역할을 모두 명시한다.
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT r.role, m.id,
       CASE WHEN r.role = 'ADMIN' THEN 'WRITE' ELSE 'NONE' END,
       NOW()
  FROM menu_screen m
  JOIN (SELECT 'ADMIN' AS role UNION ALL SELECT 'FINANCE'
        UNION ALL SELECT 'LOGISTICS' UNION ALL SELECT 'SALES') r
 WHERE m.code = 'PERSON_MEMBER'
   AND NOT EXISTS (SELECT 1 FROM role_screen_permission p
                    WHERE p.screen_id = m.id AND p.role = r.role);
