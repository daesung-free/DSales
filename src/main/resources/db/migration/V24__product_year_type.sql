-- 도서관리(32p) 데이터 완비: 상품년도·상품구분 추가.
-- 근거: 요구사항 32p 데이터항목(상품년도·상품구분) + 레거시 bookData.type(상품구분 원시값).
-- 상품년도는 레거시 bookData에 없던 신규 필드. 상품구분(product_type)=bookData.type(자유텍스트, 콘텐츠구분 SELF/EXTERNAL과는 별개 원시 분류).
ALTER TABLE products
    ADD COLUMN product_year INT         NULL COMMENT '상품년도(예: 2026). 레거시 부재→신규',
    ADD COLUMN product_type VARCHAR(30) NULL COMMENT '상품구분(레거시 bookData.type 원시값. contentType=콘텐츠구분과 별개)';
