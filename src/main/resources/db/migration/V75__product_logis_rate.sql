-- 매출프로그램 상품의 물류 단가(36p "신규등록" 신설). 근거: 발주처 회신 §1-10 원문 —
--   "현재 '신규등록'은 DSRE 상품 기준 데이터를 불러오는 기능 → **'DSRE 상품 가져오기'로 명칭 변경**하고,
--    매출프로그램에 등록된 상품을 불러와 **작업구분을 선택하면 단가가 자동 적용되는 별도 '신규등록'** 신설"
--
-- ★왜 DSRE2 tbl_logis_cost 에 넣지 않는가
--   그 테이블의 키는 DTL_CD = `tbl_product_dtl.DTL_CD`(DSRE2 상품상세 = 모의고사 시행 단위)다.
--   우리 상품(교재 등)에는 시행코드가 없어 넣을 키가 없고, DSRE2는 분리 유지라
--   우리가 새 시행코드를 발급할 수도 없다([[dsre2-stays-existing]]).
--   화면이 "물류비용 신규 등록은 시행코드가 있어야 합니다"로 막아 둔 이유가 이것이다.
--
-- ★같은 회신이 답을 정해 뒀다 — 상품별 DSRE 병행 여부 구분값
--   "Y(매출프로그램 단독 관리 — 물류비용등록 단가 자체 관리) / N(DSRE 병행 — DSRE 기준 유지)"
--   즉 단독 관리 상품의 단가는 **우리가 갖는 게 설계**다. 이 테이블이 그 자리다.
--   (우리 쪽 구분값은 products.price_visible — V60에서 ledger_visible 과 갈라 둔 필드다.)

CREATE TABLE product_logis_rate
(
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT      NOT NULL COMMENT '매출프로그램 상품',
    pack_type   INT         NOT NULL COMMENT '작업구분(work_type.pack_type)',

    -- 작업구분 기준단가를 등록 시점에 **복사해 둔다**(참조가 아니다).
    -- 참조로 두면 기준단가를 고치는 순간 과거 출고의 작업비까지 따라 바뀐다 —
    -- 발주처 §1-1 "저장된 출고 작업비는 단가 변경에 소급되지 않아야 함"에 정면으로 어긋난다.
    paper       INT         NOT NULL DEFAULT 0 COMMENT '시험지(OMR외) 개당 작업비',
    omr         INT         NOT NULL DEFAULT 0 COMMENT 'OMR 개당 작업비',
    etc         INT         NOT NULL DEFAULT 0 COMMENT '단행본',
    label       INT         NOT NULL DEFAULT 0 COMMENT '라벨',
    basic       INT         NOT NULL DEFAULT 0 COMMENT '기본작업비(인별)',
    trade       INT         NOT NULL DEFAULT 0 COMMENT '배송비(인별)',
    b_spare     CHAR(1)     NOT NULL DEFAULT 'Y' COMMENT '여분포함 여부',

    -- 개별 수정 표시. DSRE 쪽 logis_rate_override 와 같은 역할이다 —
    -- 담당자가 일부러 다른 값을 넣은 행이 일괄반영 한 번에 조용히 덮이면 잘못된 단가로 청구된다.
    overridden  BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '개별 수정됨 — 일괄반영이 건너뛴다',

    created_at  DATETIME    NOT NULL,
    created_by  VARCHAR(50) NULL,
    updated_at  DATETIME    NULL,
    updated_by  VARCHAR(50) NULL,
    deleted_at  DATETIME(6) NULL,
    deleted_by  VARCHAR(50) NULL,

    CONSTRAINT fk_plr_product FOREIGN KEY (product_id) REFERENCES products (id)
) COMMENT '매출프로그램 상품별 물류 단가(단독 관리 상품). DSRE 시행 단가와 별개 축';

-- 상품 하나에 단가는 하나다. del_key 로 "지웠다가 다시 등록"을 막지 않는다(V25 표준).
ALTER TABLE product_logis_rate
    ADD COLUMN del_key DATETIME(6)
        GENERATED ALWAYS AS (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED;
ALTER TABLE product_logis_rate
    ADD CONSTRAINT uq_product_logis_rate UNIQUE (product_id, del_key);

-- 작업구분별 일괄반영이 대상 행을 훑는다.
CREATE INDEX ix_plr_pack_type ON product_logis_rate (pack_type, deleted_at);
