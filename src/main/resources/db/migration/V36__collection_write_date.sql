-- 수금 '기장일자'. 근거: 레거시 AmtData.writeDate + 수금등록.vb:58
--   CONVERT(varchar(10),CONVERT(DATETIME,substring(writeDate,1,8)),23) as '기장일자'
-- 수금일자(collDate)와 나란히 조회되는 <b>별개 날짜</b>다 — 돈이 들어온 날과 장부에 기표한 날이
-- 다를 수 있어 재무팀이 둘을 나눠 본다.
--
-- 프론트 점검(2026-08-10)에서 "요구 23p 10종 중 기장일자 미구현"으로 지적됐는데,
-- 확인해보니 프론트뿐 아니라 우리 스키마에도 없었다.
--
-- nullable인 이유: 레거시도 빈 값을 허용한다(조회 SQL이 빈 문자열을 방어하고 있다).
-- 수금일자로 자동 채우지 않는다 — 두 날짜가 같다고 단정하면 기장일자를 따로 두는 의미가 없다.

ALTER TABLE collection
    ADD COLUMN write_date DATE NULL
        COMMENT '기장일자(회계 기표일). 수금일자와 다를 수 있어 별도 관리';

CREATE INDEX ix_collections_write_date ON collection (write_date);
