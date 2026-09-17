-- 도서코드 유니크 범위를 (분류코드 + 도서코드)로. 근거: 프론트 회신(2026-09-17) B-11 —
--   "도서코드 중복 허용(분류코드가 다르면). 레거시는 중복 가능이었는데 현재 unique로 막힘"
--
-- ★레거시가 그렇게 돼 있다 — 추측이 아니다.
--   DSLab.bookData 의 기본키가 복합키다:
--     CONSTRAINT [PR1] PRIMARY KEY CLUSTERED ([catCode] ASC, [bookCode] ASC)
--   즉 도서코드는 **분류 안에서만** 유일하다. 01·02·03 같은 짧은 코드를 분류마다 다시 쓴다.
--
-- ★우리도 이미 그 전제로 동작하는 곳이 있었다.
--   매출 엑셀 업로드가 (분류코드 + 도서코드) 조합으로 상품을 찾는다(SalesUploadService.findProduct).
--   DSRE 매출일괄등록도 lstCd+dtlCd 조합이다. 저장 쪽만 전역 유니크로 남아 어긋나 있었다.
--
-- ‼️분류코드가 없는 상품이 있으면 어떻게 되나
--   MySQL UNIQUE 는 NULL 을 서로 다른 값으로 본다 → cat_code 가 NULL 인 행은 코드가 같아도 통과한다.
--   그건 "분류 없는 도서끼리는 코드 중복 허용"이라는 뜻이라 원하는 동작이 아니다.
--   지금 데이터에 NULL 분류가 있는지는 알 수 없으므로, 빈 문자열로 맞춰 두고 유니크를 건다.

UPDATE products SET cat_code = '' WHERE cat_code IS NULL;

ALTER TABLE products DROP INDEX code;

ALTER TABLE products ADD CONSTRAINT uq_product_cat_code UNIQUE (cat_code, code);
