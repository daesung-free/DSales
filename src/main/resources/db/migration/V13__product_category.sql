-- 상품 카테고리축(분류코드/분류명). 근거: 레거시 매출액명세서.vb / 과목별매출현황.vb의 catCode·catName.
-- catCode는 계층 분류코드(첫 글자=대분류). 매출액명세서 rollup(left(catCode,1), catCode, bookCode)의 축.
-- 기존 상품엔 값 부재 → nullable. 마이그레이션/CRUD로 채움.
ALTER TABLE products ADD COLUMN cat_code VARCHAR(20);
ALTER TABLE products ADD COLUMN cat_name VARCHAR(100);

-- 대분류(left(cat_code,1))·분류(cat_code)별 집계 조회 가속.
CREATE INDEX ix_products_cat_code ON products (cat_code);
