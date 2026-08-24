-- 작업구분 마스터 + 기준단가(36p 물류비용등록). 근거: 발주처 회신 2026-08-21 + 첨부
-- 「물류비용등록_수정요청안_Page36_260821.pdf」①②.
--
-- ★왜 우리 DB에 두나 — DSRE2엔 이름을 적을 곳이 없다
--   DSRE2 {@code tbl_logis_cost.PACKTYPE}은 int 하나뿐이다:
--     '포장방법구분 1:반별봉투, 2:개별봉투, 3:개별봉투(예외,2750SET)'
--   숫자의 뜻은 컬럼 주석에만 있고 테이블이 없다. 발주처 요구는
--   "작업구분 자체도 고정값이 아니라 신규등록/수정/삭제 가능한 관리형 값"이라,
--   4번·5번 작업구분을 새로 만들면 그 뜻을 적을 자리가 DSRE2엔 없다.
--   → 이름과 기준단가는 우리가 갖고, 단가 값은 지금처럼 DSRE2에 써넣는다.
--
-- ★예외 상품을 값 비교로 판정하지 않는 이유
--   "기준단가와 다르면 예외"로 보면, 한 번도 일괄적용을 받지 않은 새 행까지 예외가 되어
--   영영 일괄적용에서 빠진다. 그래서 <b>개별 수정한 행을 명시적으로 기록</b>한다.
--   담당자가 행을 직접 고치는 순간 예외로 등록되고, 일괄적용은 그 행을 건너뛴다.
--   (PDF: "예외 처리된 항목에는 영향을 주지 않아야 하며, 예외 항목은 빨간색 등으로 구분 표시")

CREATE TABLE work_type
(
    id         BIGINT AUTO_INCREMENT PRIMARY KEY,
    pack_type  INT         NOT NULL COMMENT 'DSRE2 tbl_logis_cost.PACKTYPE 값. 이 숫자로 단가 행과 이어진다',
    name       VARCHAR(50) NOT NULL COMMENT '작업구분명(반별봉투·개별봉투 …). 담당자가 자유롭게 추가·변경',
    paper      INT         NOT NULL DEFAULT 0 COMMENT '시험지 기준단가',
    omr        INT         NOT NULL DEFAULT 0 COMMENT 'OMR 기준단가',
    etc        INT         NOT NULL DEFAULT 0 COMMENT '단행본 기준단가',
    label      INT         NOT NULL DEFAULT 0 COMMENT '라벨 기준단가',
    basic      INT         NOT NULL DEFAULT 0 COMMENT '기본작업비 기준단가',
    trade      INT         NOT NULL DEFAULT 0 COMMENT '출고비 기준단가',
    use_yn     BOOLEAN     NOT NULL DEFAULT TRUE,
    sort_order INT         NOT NULL DEFAULT 0,
    created_at DATETIME    NOT NULL,
    created_by VARCHAR(50) NULL,
    updated_at DATETIME    NULL,
    updated_by VARCHAR(50) NULL,
    -- pack_type이 단가 행과 잇는 키라 유일해야 한다(논리삭제를 쓰지 않는 이유).
    CONSTRAINT uq_work_type_pack UNIQUE (pack_type)
) COMMENT '작업구분 마스터 — 이름·기준단가. DSRE2 PACKTYPE 숫자에 뜻을 붙인다';

-- 레거시가 쓰던 3종을 그대로 심는다(단가는 PDF 예시값).
INSERT INTO work_type (pack_type, name, paper, omr, etc, label, basic, trade, sort_order, created_at)
VALUES (1, '반별봉투', 50, 50, 50, 0, 0, 0, 1, NOW()),
       (2, '개별봉투', 40, 40, 40, 100, 100, 100, 2, NOW()),
       (3, '개별봉투(SET)', 40, 40, 40, 0, 100, 100, 3, NOW());

CREATE TABLE logis_rate_override
(
    dtl_cd     INT PRIMARY KEY COMMENT 'DSRE2 시행코드. 이 행은 개별 수정된 예외라 일괄적용에서 제외된다',
    created_at DATETIME    NOT NULL,
    created_by VARCHAR(50) NULL COMMENT '누가 예외로 만들었는지 — 나중에 왜 안 바뀌는지 물을 때 답이 된다'
) COMMENT '개별 수정된 물류단가 행(예외). 작업구분 일괄 반영이 건너뛴다';
