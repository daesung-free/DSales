-- 영업(SALES) 역할의 [매출관리] 그룹을 **조회**로 좁힌다.
--
-- 근거: 발주처 「사용자권한_구조_설계_예시(수정)」 1단계 표 —
--   영업 = 주문/출고관리 ○ · **매출관리 ◐** · 마감관리 ◐ · 물류작업 ◐ · 기초관리 ◐
--   (◐ = 조회만, ○ = 등록·수정 가능)
--
-- ‼️V52 는 SALES 에 `ELSE 'WRITE'` 를 깔아, 명시적으로 뺀 화면(입고·폐기·마감·물류·기초)을
--   제외한 나머지가 전부 WRITE 로 열렸다. 그 바람에 매출·위탁·정산·대시보드까지 등록 권한이 갔다.
--   프론트 실측(2026-10-22): "화면은 매트릭스대로 잠그지만 요청을 직접 보내면 서버가 받는다".
--   화면만 잠그는 것은 잠근 것이 아니다 — 서버가 기본값을 들고 있어야 한다.
--
-- ★주문/출고관리(ORDER 그룹)는 그대로 둔다 — 매트릭스에서 영업은 ○ 다.
--   2단계 표의 입고/대체등록·폐기등록 N 은 V52 가 이미 NONE 으로 막아 두었다.
UPDATE role_screen_permission p
   JOIN menu_screen m ON m.id = p.screen_id
   SET p.permission = 'READ',
       p.updated_at = NOW()
 WHERE p.role = 'SALES'
   AND m.menu_group = 'SALES'
   AND p.permission = 'WRITE';
