-- 세부구분에 '기타고사' · '특강' 추가 — **과거 데이터 보존용**.
-- 근거: 프론트 실측 2026-10-16 A-3 + 레거시 소스 실측.
--
-- 레거시는 분류코드 첫 글자로 구분을 나눈다(순매출조회.vb:38~54 · 세금계산신고.vb:56~62):
--     H(6번째≠Z) → 교재   ·   M → 모의고사   ·   S → 기타고사   ·   N → 특강
--     I, H____Z  → IC
-- 그런데 발주처 확정 세부구분 8종(V41)에는 **'기타고사'와 '특강'이 없다.**
-- 둘은 레거시에 실재한 구분이고 이관 도서 14,808권 중 상당수가 거기 속한다
-- (S 2,379권 · N 23권). 받을 칸이 없으면 그 상품의 대분류가 비고,
-- 대분류로 집계하는 화면(외상매출현황 상품군 분해 · 계산서 품목 대분류)이
-- 통째로 동작하지 않는다.
--
-- ★그래서 IC 와 **똑같은 방식**으로 둔다 — 행은 만들되 `use_yn=FALSE` 로 목록에서 뺀다.
--   · 과거 상품·매출은 어느 구분이었는지 보존된다(대분류 집계가 동작한다)
--   · 신규 등록 화면의 선택지에는 안 나온다(발주처 확정 8종만 노출)
--   V41 의 IC 주석과 같은 판단이다: "미사용이나 데이터는 보존해야 하니 화면상 숨김".
--
-- ⏸발주처 확인 대상: 과거 '기타고사'·'특강' 을 현행 8종 중 하나로 접을지
--   (예: 특강 → 지자체). 접기로 하면 이 두 행의 상품을 옮기고 행을 지우면 된다.
--   그때까지는 보존이 맞다 — 지금 임의로 접으면 과거 집계가 조용히 달라진다.
INSERT INTO sales_divisions (code, name, major_category, use_yn, sort_order, created_at)
SELECT '기타고사', '기타고사', 'ETC_EXAM', FALSE, 97, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sales_divisions d WHERE d.code = '기타고사');

INSERT INTO sales_divisions (code, name, major_category, use_yn, sort_order, created_at)
SELECT '특강', '특강', 'SPECIAL_LECTURE', FALSE, 98, NOW()
WHERE NOT EXISTS (SELECT 1 FROM sales_divisions d WHERE d.code = '특강');
