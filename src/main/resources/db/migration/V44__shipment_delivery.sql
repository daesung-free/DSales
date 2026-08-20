-- 작업요청서(26p) 발송구분·수령인·송장. 근거: 정본 26p [업데이트 2026-07-25 ★클라이언트 정정★]
--   "별도 기초관리 메뉴 신설 대신, 작업 대기 리스트의 '상태변경' 옆에 '발송구분(택배/화물)' 필드 추가.
--    '택배' 선택 시 담당자 정보가 노출되어 물류가 거래명세서를 보고 택배/화물 여부 판단 후 발송"
--
-- ★프론트는 이미 이 컬럼들을 그리고 있다
--   프론트 점검(23-작업요청서) 기준 작업대기리스트 컬럼 —
--     요청일 | 등록처 | 거래처 | 학교/학원 | 분류명 | 도서명 | 수량 | 출고창고 | 발송구분 | 수령인 | 상태
--   '발송구분'·'수령인'을 우리가 안 주고 있었다. 화면만 있고 데이터가 없던 자리를 채운다.
--
-- ★송장번호·택배사는 '기록'까지만이다
--   정본 26p의 미확정 항목은 "'본인 택배현황조회(송장번호)' 기능 신설 여부"인데, 그건
--   특약점 사이트(order)에서 구매자가 본인 배송을 조회하는 화면 이야기다(Phase4·프론트 트랙).
--   물류가 발송 건에 송장번호를 적어두는 것은 그와 별개이고, 적어두지 않으면
--   나중에 그 조회 기능을 붙일 때 채울 데이터 자체가 없다.
--   ⚠️택배사 API 연동은 하지 않는다(CJ 송장 API 연동 여부는 물류팀 인터뷰 회신 대기).

ALTER TABLE shipment
    ADD COLUMN delivery_type  VARCHAR(10) NULL COMMENT '발송구분 COURIER(택배)/FREIGHT(화물). 26p 확정',
    ADD COLUMN receiver_name  VARCHAR(50) NULL COMMENT '수령인(택배일 때 담당자 정보)',
    ADD COLUMN receiver_phone VARCHAR(30) NULL COMMENT '수령인 연락처',
    ADD COLUMN courier_name   VARCHAR(30) NULL COMMENT '택배사(예: CJ대한통운). 수기·엑셀 일괄 입력',
    ADD COLUMN tracking_no    VARCHAR(50) NULL COMMENT '송장번호. 수기·엑셀 일괄 입력';

-- 송장번호로 되짚는 조회(택배사 회신 대조·문의 응대)
CREATE INDEX ix_shipment_tracking ON shipment (tracking_no);

-- 물류 화면이 발송구분으로 거른다
CREATE INDEX ix_shipment_delivery ON shipment (trade_date, delivery_type);
