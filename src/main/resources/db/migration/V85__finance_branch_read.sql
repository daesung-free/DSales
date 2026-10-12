-- 재무 역할에 **지사 목록 읽기**를 연다(프론트 실측 2026-10-12 C-1).
--
-- 근거: 매출 화면의 「거래처 › 학교」 트리가 지사 정보를 `/orders/branches` 에서 가져온다.
--   재무는 매출관리가 ◐(조회)인데 주문 화면군(`/orders/**`)이 통째로 비노출이라 403 이 나고,
--   그 결과 **재무 계정에서만 거래처 트리가 지사 묶음 없이** 나왔다.
--
-- ★주문 화면 전체를 열지 않는다. 필요한 것은 "지사 목록"이고, 그건 거래처 마스터 성격의
--   읽기 자료다. `/orders/**` 를 READ 로 열면 주문 조회·등록 화면까지 재무 메뉴에 뜬다
--   (권한표상 재무는 주문출고 비노출이 확정이다).
--   → `/orders/branches` 만 별도 화면으로 떼어 **전 역할 조회**로 둔다.
--     마감상태 조회(V80)와 같은 방식이다.
--
-- ‼️sort_order 가 ORDER_SHIP(20)보다 **앞**이어야 한다. 화면 매칭은 먼저 걸리는 패턴이 이기므로
--   뒤에 두면 `/orders/**` 에 먹혀 이 줄이 아무 일도 하지 않는다.
INSERT INTO menu_screen (code, name, menu_group, path_pattern, sort_order, created_at)
VALUES ('ORDER_BRANCH', '지사목록조회', 'ORDER', '/orders/branches/**', 15, NOW());

-- 전 역할 조회(쓰기는 없는 경로다). 역할은 4종뿐이다 — 없는 역할명을 넣으면 권한표 로딩에서
-- enum 변환이 터져 모든 요청이 500 이 된다(V80에서 실제로 겪었다).
INSERT INTO role_screen_permission (role, screen_id, permission, updated_at)
SELECT r.role, ms.id, 'READ', NOW()
  FROM menu_screen ms
  CROSS JOIN (SELECT 'ADMIN' AS role UNION ALL SELECT 'FINANCE'
              UNION ALL SELECT 'LOGISTICS' UNION ALL SELECT 'SALES') r
 WHERE ms.code = 'ORDER_BRANCH';
