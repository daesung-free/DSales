-- 대분류 · 세부구분 축 신설. 근거: 발주처 회신 2026-08-20「상품 마스터 대분류/매출구분」.
--
-- ★ 무엇이 문제였나
--   지금까지 products.sales_division(매출구분)은 길이 30짜리 자유 문자열이라
--   같은 뜻의 값이 여러 표기로 들어가도 막을 방법이 없었고, 그 위에 무엇으로 집계할지도 없었다.
--   회신이 축을 둘로 못박았다 —
--     · 대분류   = 5종 고정(모의고사/교재/기타고사/특강/기타). 집계 기준.
--     · 세부구분 = 그 아래 값. "연도별로 바뀔 수 있어 사용자가 직접 추가/삭제하는 관리형 값" → 마스터 필요.
--   (기존 '매출구분'이 곧 이 세부구분이다. 명칭만 바뀐다.)
--
-- ★ 대분류를 상품에 저장하지 않는 이유
--   대분류는 세부구분에서 파생된다(D모의고사 → 기타고사). 상품에도 복사해두면
--   나중에 매핑이 바뀔 때 두 곳이 어긋나고, 어느 쪽이 맞는지 알 수 없게 된다.
--   매핑은 이 표 한 곳에만 두고 조인으로 읽는다. (재고 단일공식과 같은 규율)
--
-- ★ 레거시 교차검증 — 회신의 5종은 새로 만든 값이 아니다.
--   CommonDB.vb:151~161이 분류코드 첫 글자로 같은 축을 만들고 있었다.
--     left(catCode,1)='H'→교재 / in('I','S')→기타고사 / ='M'→모의고사 / ='N'→특강
--   레거시는 코드 글자에 숨겨 놨고, 우리는 필드로 드러내는 것뿐이다.
--   IC(=I)도 레거시에서 기타고사에 묶여 있었다 → 회신의 "미사용이나 데이터 보존, 화면 숨김"과 일치.

CREATE TABLE sales_divisions
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    code           VARCHAR(30)  NOT NULL COMMENT '세부구분 코드(불변 키). products.sales_division이 이 값을 가리킨다',
    name           VARCHAR(50)  NOT NULL COMMENT '세부구분 명칭(표시용, 연도별 변경 가능)',
    major_category VARCHAR(20)  NOT NULL COMMENT '소속 대분류(MajorCategory). 집계 기준',
    use_yn         BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '사용여부. 상품이 쓰던 값은 지우지 않고 여기서 끈다',
    sort_order     INT          NOT NULL DEFAULT 0 COMMENT '화면 정렬 순서',
    created_at     DATETIME     NOT NULL,
    created_by     VARCHAR(50)  NULL,
    updated_at     DATETIME     NULL,
    updated_by     VARCHAR(50)  NULL,
    -- ‼️code는 products.sales_division의 조인 대상이라 UNIQUE여야 한다(논리삭제 del_key 방식을 쓰지 않는 이유).
    CONSTRAINT uq_sales_division_code UNIQUE (code)
) COMMENT '세부구분 마스터(구 매출구분) — 대분류 매핑을 갖는 유일한 곳';

-- 회신 매핑표 그대로. 코드=명칭으로 시작하되, 명칭이 바뀌어도 코드는 두어 상품 연결이 끊기지 않게 한다.
INSERT INTO sales_divisions (code, name, major_category, use_yn, sort_order, created_at)
VALUES ('모의고사', '모의고사', 'MOCK_EXAM', TRUE, 1, NOW()),
       ('교재', '교재', 'TEXTBOOK', TRUE, 2, NOW()),
       ('D모의고사', 'D모의고사', 'ETC_EXAM', TRUE, 3, NOW()),
       ('학원콘텐츠', '학원콘텐츠', 'ETC_EXAM', TRUE, 4, NOW()),
       ('D:VOCA', 'D:VOCA', 'TEXTBOOK', TRUE, 5, NOW()),
       ('지자체', '지자체', 'SPECIAL_LECTURE', TRUE, 6, NOW()),
       ('기타', '기타', 'ETC', TRUE, 7, NOW()),
       -- IC — 회신 "현재 미사용이나 데이터는 보존해야 하니 화면상 숨김 처리".
       -- 그래서 행은 두되 use_yn=FALSE로 목록에서 뺀다. 지우면 IC로 등록된 과거 상품·매출이
       -- 어느 구분이었는지 알 수 없게 된다(레거시에서도 IC는 실재 분류였다 — CommonDB.vb의 'I').
       ('IC', 'IC', 'IC', FALSE, 99, NOW());

-- 이미 등록된 상품이 쓰던 값 중 매핑표에 없는 것은 '기타'로 받아둔다.
-- 버리면 그 상품의 세부구분이 조인에서 빠져 집계에서 사라진다 — 담당자가 대분류만 다시 지정하면 된다.
INSERT INTO sales_divisions (code, name, major_category, use_yn, sort_order, created_at)
SELECT DISTINCT p.sales_division, p.sales_division, 'ETC', TRUE, 900, NOW()
FROM products p
WHERE p.sales_division IS NOT NULL
  AND p.sales_division <> ''
  AND NOT EXISTS (SELECT 1 FROM sales_divisions d WHERE d.code = p.sales_division);

-- 조인 성능(리포트가 대분류로 집계한다)
CREATE INDEX ix_sales_division_major ON sales_divisions (major_category, sort_order);
