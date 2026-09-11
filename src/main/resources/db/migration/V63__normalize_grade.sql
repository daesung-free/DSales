-- 학년 표기 통일. 운영 데이터에 '고3' 9건 · '3' 1건이 섞여 있었다.
--
-- ★두 값은 필터에서 서로 안 잡힌다. 같은 고3 상품인데 조회 조건에 따라 한쪽만 나오고,
--   리포트에서는 두 줄로 쪼개진다. 오류가 아니라서 아무도 모른 채 숫자가 어긋난다.
--
-- 저장값은 **숫자만**으로 맞춘다 — 프론트 셀렉트가 그렇게 쓰고 있다
-- (logisticsScreens.ts: value '1'·'2'·'3' / label 'N학년').
-- 화면 표기(N학년)는 서버가 gradeName 으로 따로 내려준다.
UPDATE products SET grade = '1' WHERE grade IN ('고1', '1학년', '고등학교1학년');
UPDATE products SET grade = '2' WHERE grade IN ('고2', '2학년', '고등학교2학년');
UPDATE products SET grade = '3' WHERE grade IN ('고3', '3학년', '고등학교3학년');
