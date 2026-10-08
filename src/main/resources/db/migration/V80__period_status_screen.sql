-- 월 잠금 여부 조회를 전 역할에 연다(2026-10-08 [C]18).
--
-- 근거: 프론트 회신 — "물류 역할도 월마감 상태 읽기. 지금은 입고·폐기 화면이 마감된 달을
--       미리 알려 주지 못한다". 마감된 달이면 저장 단계에서야 PERIOD_LOCKED 가 떠서
--       담당자는 다 입력한 뒤에 막힌다.
--
-- ★마감 화면(/closing/**)을 열어 주지 않는다. 그 화면에는 마감 메모·처리자·물류작업비
--   동결 같은 재무 정보가 함께 실린다. 필요한 것은 "이 달이 잠겼나" 한 비트뿐이라
--   그 한 비트만 내리는 경로를 따로 두고 거기만 연다.
--
-- ‼️sort_order 가 CLOSING(90)보다 **앞**이어야 한다. 화면 매칭은 먼저 걸리는 패턴이 이기므로
--   뒤에 두면 /closing/** 에 먹혀 이 줄이 아무 일도 하지 않는다.
INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at)
VALUES ('PERIOD_STATUS', '마감상태조회', 'CLOSING', '/closing/period-status/**', 85, NOW());

-- 전 역할 조회. 쓰기는 애초에 없는 경로다(GET 전용).
--
-- ‼️역할은 **4종뿐이다**(ADMIN/FINANCE/LOGISTICS/SALES). 조회전용 VIEWER 는 발주처 지시로
--   없앴다(Role.java 주석 참고 — "3단계 구조로 같은 효과를 낼 수 있어 별도 역할로 두지 않음").
--   여기에 없는 역할명을 한 줄이라도 넣으면 권한표를 읽을 때 enum 변환이 터져
--   **모든 사용자의 모든 요청이 500** 이 된다(실제로 그렇게 넣었다가 테스트에서 잡혔다).
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT r.role, ms.id, 'READ', NOW()
  FROM menu_screen ms
  CROSS JOIN (SELECT 'ADMIN' AS role UNION ALL SELECT 'FINANCE'
              UNION ALL SELECT 'LOGISTICS' UNION ALL SELECT 'SALES') r
 WHERE ms.code = 'PERIOD_STATUS';
