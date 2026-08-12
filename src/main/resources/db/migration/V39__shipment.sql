-- 발송(작업) 단위. 레거시 DSLab.dbo.sendData 대응.
-- 근거: 작업요청서.vb(쓰기) / 작업결과.vb(읽기) / UC_TabPages.vb:752(매출등록 시 INSERT).
--
-- ★왜 매출과 별도인가
--   매출은 "얼마 팔렸나", 발송은 "그걸 언제 뽑아서 몇 박스로 언제 보냈나"다.
--   지금까지 우리에겐 뒤쪽을 담을 자리가 없어 물류 화면(작업요청서·작업결과)을 만들 수 없었다.
--
-- ★레거시 흐름 그대로 옮긴다
--   매출등록      → 발송 건 생성(박스 0, 날짜 비어 있음)   UC_TabPages.vb:752
--   작업요청서 출력 → printed_at 기록                      작업요청서_신청비교.vb:280
--   작업요청서 입력 → box_count / sent_date / send_memo    작업요청서.vb:915
--   작업결과      → 조회만(쓰기문 0건, 그리드 편집 비활성)  작업결과.vb
--
-- ★completed_at 은 레거시에서 한 번도 쓰지 않는다
--   작업결과·작업요청서가 '완료' 컬럼으로 읽기만 하고, 값을 넣는 코드가 전 소스에 없다
--   (주석 처리된 이관 코드에만 등장). 컬럼은 두되 우리도 쓰기 경로를 만들지 않는다 —
--   레거시에 없는 동작을 임의로 만들면 화면 의미가 달라진다. 필요하면 발주처 확인 후 붙인다.

CREATE TABLE shipment (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    trade_class   VARCHAR(20)  NULL     COMMENT '분류(레거시 tradeClass: IC/교재 등). 매출구분에서 따온다',
    trade_date    DATE         NOT NULL COMMENT '거래일자',
    trade_seq     INT          NOT NULL DEFAULT 1 COMMENT '같은 일자·거래처·학교의 순번',
    partner_id    BIGINT       NOT NULL COMMENT '거래처',
    school_code   VARCHAR(30)  NULL     COMMENT '학교/학원 코드(없을 수 있다)',
    school_name   VARCHAR(100) NULL     COMMENT '학교/학원명',

    printed_at    DATETIME(6)  NULL     COMMENT '작업요청서 출력 시각. 작업결과의 ○(출력)',
    completed_at  DATETIME(6)  NULL     COMMENT '완료 시각. ⚠️레거시에 쓰기 경로가 없다(읽기만)',
    box_count     INT          NOT NULL DEFAULT 0 COMMENT '박스 수(물류 입력)',
    sent_date     DATE         NULL     COMMENT '발송일(물류 입력)',
    send_memo     VARCHAR(1000) NULL    COMMENT '발송메모(물류 입력, 예: 착불)',
    memo          VARCHAR(500) NULL     COMMENT '비고',
    deleted_at    DATETIME(6)  NULL     COMMENT '논리삭제. 레거시 isDelete 대응',

    created_at    DATETIME     NOT NULL,
    created_by    VARCHAR(50)  NULL,
    updated_at    DATETIME     NULL,
    updated_by    VARCHAR(50)  NULL,
    CONSTRAINT fk_shipment_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) COMMENT '발송(작업) 단위 — 작업요청서가 쓰고 작업결과가 읽는다';

-- 같은 (일자·거래처·학교·분류)에는 발송 건이 하나다. 매출을 여러 번 등록해도 같은 건에 묶인다.
-- school_code가 NULL일 수 있어 COALESCE로 정규화한다(NULL은 서로 다르게 취급돼 중복이 뚫린다).
CREATE UNIQUE INDEX ux_shipment_key ON shipment (
    trade_date, partner_id, (COALESCE(school_code, '')), (COALESCE(trade_class, '')), trade_seq
);

-- 물류 화면의 주 질의: 기간 + 출력여부
CREATE INDEX ix_shipment_date ON shipment (trade_date, printed_at);
