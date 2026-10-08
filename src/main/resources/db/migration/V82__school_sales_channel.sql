-- 판매채널. 근거: 레거시 `조회.Designer.vb:365` — 조회화면 콤보 라벨이 "판매채널"이고,
--   그 목록은 `CommonDB.vb:475` `SELECT type1 FROM schDataDT GROUP BY type1`,
--   필터는 `조회.vb:1203~1209` 다.
--
-- ★발주처에 물을 것이 아니었다. 프론트가 「판매채널 항목 — 클라이언트 답 후」로 올려 둔 건이지만,
--   레거시에 정의와 값 출처가 다 있다. schDataDT 는 "학교데이터에 구분 추가"(테이블 주석)로
--   schCode·type1·type2 세 칸뿐이고, **type2 는 이미 우리 '학교구분'으로 들어와 있다**
--   (조회.vb:1528 `min(type2) as '학교구분'`). 짝이 되는 type1 만 빠져 있었다.
--
-- ★자유문자열이다 — 레거시가 코드표를 두지 않고 학교 행에 적힌 값을 GROUP BY 로 모아 목록을 만든다.
--   우리도 같게 둔다. 고정 enum 으로 만들면 발주처가 채널을 하나 더 쓰기 시작할 때 배포가 필요하다.
--
-- 레거시 필터 규칙 그대로 옮긴다(조회.vb:1203~):
--   · '< 전체 >'   → 조건 없음
--   · '< 미분류 >' → type1 이 NULL 이거나 공백
--   · 그 외        → LIKE 부분일치

ALTER TABLE schools
    ADD COLUMN sales_channel VARCHAR(20) NULL
        COMMENT '판매채널 — 레거시 schDataDT.type1. 자유문자열(메가스터디·자체몰·B2B 등)';

CREATE INDEX ix_schools_sales_channel ON schools (sales_channel);
