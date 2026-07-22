-- 거래처 세무 정보(계산서 공급받는자용). 근거: 레거시 custData(custNum/bossName/addr/tradeStatus/eMail).
-- 계산서신고(홈택스)·수익신고의 공급받는자/사업자번호에 사용. 거래처가 입력하는 마스터 데이터.
ALTER TABLE partners ADD COLUMN biz_no     VARCHAR(20);   -- 사업자번호
ALTER TABLE partners ADD COLUMN boss_name  VARCHAR(50);   -- 대표자 성명
ALTER TABLE partners ADD COLUMN addr1      VARCHAR(200);  -- 주소
ALTER TABLE partners ADD COLUMN addr2      VARCHAR(200);  -- 상세주소
ALTER TABLE partners ADD COLUMN biz_status VARCHAR(100);  -- 업태
ALTER TABLE partners ADD COLUMN biz_item   VARCHAR(100);  -- 종목
ALTER TABLE partners ADD COLUMN email1     VARCHAR(100);
ALTER TABLE partners ADD COLUMN email2     VARCHAR(100);
