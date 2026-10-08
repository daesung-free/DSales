-- 계산서신고 수기 보정. 근거: 발주처 1차회신(2026-10-02) #24 —
--   "기본적으로 세팅된 내역에 수정/저장이 가능하도록 요청드린 건입니다.(수기 보정)
--    현재 매출프로그램에서 계산서 신고 내역은 해당월의 매출이 모두 해당월 말일자로 조회되는
--    구조입니다. 동일한 구조로 설계 부탁드리며, 조회 시 과세/비과세 구분이 필요합니다."
--
-- ★계산서 내역 자체는 **저장하지 않는다**. 지금처럼 매출에서 파생해 만들고, 그 위에
--   담당자가 손댄 값만 이 표에 얹는다. 내역을 통째로 저장해 버리면 매출을 고쳤을 때
--   계산서가 따라가지 않아 장부와 신고가 조용히 갈린다.
--
-- ★키 = (작성일자 · 거래처 · 과세구분). 계산서 한 장의 단위가 그것이다
--   (월 단위 조회면 작성일자가 그 달 말일이라 자연히 월×거래처×과세구분이 된다).
--
-- ‼️금액 보정도 받되 **원값을 지우지 않는다**. 조회 응답이 보정 여부와 원래 숫자를 함께 내려
--   "장부와 다르다"가 드러나게 한다. 숨기면 나중에 세무 소명 때 아무도 설명하지 못한다.
--   품목은 보정 대상이 아니다 — 품목·수량이 틀렸다면 고칠 곳은 매출이다(매출 수정 API).

CREATE TABLE tax_invoice_adjustment (
    id            BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    issue_date    DATE        NOT NULL COMMENT '작성일자(계산서 한 장의 기준일 = 조회 종료일)',
    partner_id    BIGINT      NOT NULL COMMENT '공급받는자 거래처',
    tax_bucket    VARCHAR(10) NOT NULL COMMENT 'FREE(면세 05) / TAXABLE(과세 01)',

    -- 공급받는자 정보 보정(거래처 마스터가 낡았거나 신고용 표기가 다른 경우)
    partner_name  VARCHAR(100) NULL COMMENT '상호 보정',
    biz_no        VARCHAR(20)  NULL COMMENT '사업자번호 보정',
    boss_name     VARCHAR(50)  NULL COMMENT '대표자 보정',
    address       VARCHAR(255) NULL COMMENT '사업장주소 보정',
    biz_status    VARCHAR(50)  NULL COMMENT '업태 보정',
    biz_item      VARCHAR(50)  NULL COMMENT '종목 보정',
    email1        VARCHAR(100) NULL COMMENT '이메일1 보정',
    email2        VARCHAR(100) NULL COMMENT '이메일2 보정',

    -- 금액 보정(선택). NULL 이면 파생값 그대로 쓴다.
    supply_total  BIGINT       NULL COMMENT '공급가액 합계 보정',
    tax_total     BIGINT       NULL COMMENT '세액 합계 보정',

    receipt_type  VARCHAR(2)   NULL COMMENT '영수(01)/청구(02). 미지정이면 기본 02',
    memo          VARCHAR(500) NULL COMMENT '비고',
    reason        VARCHAR(500) NULL COMMENT '왜 고쳤나 — 감사에서 실제로 묻는 것',

    created_at    DATETIME     NULL,
    created_by    VARCHAR(50)  NULL,
    updated_at    DATETIME     NULL,
    updated_by    VARCHAR(50)  NULL,

    CONSTRAINT uq_tax_adj UNIQUE (issue_date, partner_id, tax_bucket),
    CONSTRAINT fk_tax_adj_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) COMMENT '계산서신고 수기 보정 — 파생 내역 위에 얹는 값만 둔다';
