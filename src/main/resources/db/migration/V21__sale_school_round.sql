-- 매출 엑셀 업로드(발주처 요청 추가1) — 레거시 salesData의 정식 필드였으나 재구축 시 누락된 것 복원.
-- 학교(schCode/schName): 매출의 세부 거래단위(학교/학원). 회차(bookReqSeq): 세트 상품 회차.
-- 근거: 레거시 DsSales salesData.schCode/schName/bookReqSeq + 매출 업로드양식_샘플.xlsx.
ALTER TABLE sales
    ADD COLUMN school_code VARCHAR(30)  NULL COMMENT '학교/학원 코드(salesData.schCode)',
    ADD COLUMN school_name VARCHAR(100) NULL COMMENT '학교/학원명(salesData.schName)',
    ADD COLUMN book_round   INT          NULL COMMENT '세트 상품 회차(salesData.bookReqSeq)';
