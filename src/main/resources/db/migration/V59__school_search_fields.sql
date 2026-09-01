-- 학교/학원검색(29p)용 필드 보강. 근거: 레거시 `학교검색.vb` 조회 SQL +
-- 프론트 「백엔드 전달 2026-08-20」 §B-3(대응 API 없음) +
-- 발주처 화면검토(2026-08-31) "화면명을 '학교검색'에서 '학교/학원검색'으로".
--
-- 레거시 원문(schData 별칭 그대로) —
--   schCode 학교코드 · schName 학교명 · cityCode 지역코드 · custCity 지역명
--   custLoc 특약점L · custName 특약점N · concat(custLoc,' ',custName) 특약점LN
--   mCustName 특약점_모의고사 · iCustName 특약점_IC
--
-- ★<b>특약점이 상품군별로 다르다</b>는 것이 이 화면의 핵심이다 —
--   "같은 학교라도 상품군에 따라 담당 특약점이 다르다"(프론트 §B-3).
--   그래서 거래처 하나로는 표현이 안 되고 모의고사·IC를 따로 들고 있어야 한다.
--
-- ‼️단 레거시는 그 두 컬럼을 **주석 처리해 화면에 안 띄운다**(학교검색.vb:44-45).
--   대신 **검색 조건에는 살아 있다** — `custName like ? or mCustName like ? or iCustName like ?`.
--   즉 "특약점명으로 찾을 때는 세 축을 다 뒤지되, 결과 표에는 대표 특약점만 보여준다".
--   그 동작을 그대로 옮긴다. 화면에 띄울지는 발주처가 정할 일이라 응답에는 담아 둔다.

ALTER TABLE schools
    ADD COLUMN city_code    VARCHAR(20)  NULL COMMENT '지역코드(레거시 cityCode). 지역명(city)과 짝',
    ADD COLUMN partner_loc  VARCHAR(100) NULL COMMENT '특약점L — 특약점 소재(레거시 custLoc)',
    ADD COLUMN mock_partner_code VARCHAR(30)  NULL COMMENT '모의고사 담당 특약점 코드(레거시 mCustCode)',
    ADD COLUMN mock_partner_name VARCHAR(100) NULL COMMENT '모의고사 담당 특약점명(레거시 mCustName)',
    ADD COLUMN ic_partner_code   VARCHAR(30)  NULL COMMENT 'IC 담당 특약점 코드(레거시 iCustCode)',
    ADD COLUMN ic_partner_name   VARCHAR(100) NULL COMMENT 'IC 담당 특약점명(레거시 iCustName)';

-- 검색이 학교코드·학교명·지역코드·특약점명 4종으로 들어온다. 앞의 둘만 인덱스를 준다 —
-- 지역·특약점은 LIKE '%…%' 라 인덱스를 못 타고, 학교 마스터는 수만 건 규모가 아니다.
CREATE INDEX ix_schools_name ON schools (school_name);
