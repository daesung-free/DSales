-- 매출목표 축 확장. 근거: 자료요청서 1-6 회신(2026-08-11)으로 받은 실제 목표 데이터가
-- 기존 구조에 한 줄도 안 들어가서 고친다.
--
-- 받은 데이터:            2026 | 연간 | 더프리미엄   | 9,454,430,300
--                        2025 | 연간 | 더프리미엄   | 8,794,691,150  (실적)
-- 기존 구조가 막던 지점:
--   ① month INT NOT NULL + CHECK(1~12) → '연간'을 넣을 자리가 없다.
--      ‼️원인은 우리에게 있다 — 자료요청서 1-6 양식을 "2026 | 1월 | …" 월 단위 예시로 보냈다.
--   ② 대상이 product_id(상품 1건) 아니면 null(전사) 둘뿐 → 그 사이의 '사업부문'이 없다.
--      '기준대상'이라는 컬럼명도 우리가 정의 없이 물었고 예시는 '전사총매출' 하나뿐이었다.
--   ③ 2025 실적을 담을 곳이 없다. 우리는 새 데이터로 시작해 2025 매출이 DB에 없는데,
--      자료요청서에 "2026년 목표와 2025년 실적만 주시면 비교 그래프까지 정상 표시"라고
--      써서 요청했다 — 그러면 그 실적을 저장해둬야 전년비가 나온다.

-- ① 연간 목표 허용: CHECK 제거 후 nullable(NULL = 연간)
ALTER TABLE sales_target DROP CHECK sales_target_chk_1;
ALTER TABLE sales_target
    MODIFY COLUMN month INT NULL COMMENT '목표 월(1~12). NULL이면 연간 목표';
ALTER TABLE sales_target
    ADD CONSTRAINT sales_target_chk_month CHECK (month IS NULL OR month BETWEEN 1 AND 12);

-- ② 대상 축: 전사 / 사업부문 / 상품
ALTER TABLE sales_target
    ADD COLUMN scope VARCHAR(20) NOT NULL DEFAULT 'COMPANY'
        COMMENT '목표 대상 COMPANY(전사)/DIVISION(사업부문)/PRODUCT(상품)',
    ADD COLUMN scope_key VARCHAR(100) NULL
        COMMENT 'scope=DIVISION일 때 사업부문명(더프리미엄·D모의고사·학원 컨텐츠·외부 교재 등)';

-- 기존 행 정합: product_id가 있던 행은 상품 목표였다.
UPDATE sales_target SET scope = 'PRODUCT' WHERE product_id IS NOT NULL;

-- ③ 목표/실적 구분 — 같은 표에 두면 연·대상별로 나란히 비교된다.
ALTER TABLE sales_target
    ADD COLUMN entry_type VARCHAR(10) NOT NULL DEFAULT 'TARGET'
        COMMENT 'TARGET(목표)/ACTUAL(확정 실적 — 이관하지 않은 과거연도 실적 보관용)';

-- 유니크 재정의. NULL은 서로 다르게 취급되어 중복이 뚫리므로 COALESCE로 정규화한다
-- (기존 인덱스도 coalesce(product_id,-1)을 쓰고 있었다 — 같은 방식).
ALTER TABLE sales_target DROP INDEX ux_sales_target;
CREATE UNIQUE INDEX ux_sales_target ON sales_target (
    fiscal_year,
    (COALESCE(month, 0)),
    scope,
    (COALESCE(scope_key, '')),
    (COALESCE(product_id, -1)),
    entry_type
);
