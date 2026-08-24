-- 물류 작업비 마감 스냅샷. 근거: 발주처 회신 2026-08-21 —
-- "이미 계산·청구되어 저장된 과거 출고 작업비는 이후 단가가 바뀌어도 소급 변경되지 않고
--  그대로 고정되어야 합니다" / "마감 확정된 월의 물류작업비 단가에는 소급 변경되지 않아야".
--
-- ★왜 결과를 굳혀야 하나 — 단가를 잠그는 것으로는 안 된다
--   물류비는 저장된 값이 아니라 조회할 때마다 DSRE2에서 다시 계산한다.
--   그래서 단가를 고치면 이미 청구가 끝난 작년 6월 금액까지 같이 바뀐다.
--   단가는 월별이 아니라 전역이라 "6월치 단가만 잠근다"가 성립하지 않는다.
--   → 마감하는 순간 그 달 결과를 통째로 저장하고, 이후 그 달은 저장분을 돌려준다.
--
-- ★가장 잘게 저장한다(상품×학년×시행×신청×거래처)
--   화면이 신청 축·거래처 축 두 가지로 묶고, 구분(전체/일반/사고)·취소포함 옵션까지 있다.
--   총계만 굳히면 신청 단위 조회는 여전히 실시간이라 두 화면 숫자가 갈린다.
--   조합마다 굳히면 경우의 수가 폭발한다. 원자 단위 하나만 저장하면 어떤 조합이든 다시 만들어진다.
--   그래서 구분(apply_gn)·취소(canceled)도 값으로 담는다 — 걸러서 저장하면 그 조건으로만 볼 수 있다.
--
-- ★기준 날짜는 접수일자(REQ_DATE)
--   물류비의 시간축은 데이터상 이것 하나뿐이다(레거시 기간조회·우리 집계 모두 REQ_DATE 기준).

CREATE TABLE logis_cost_snapshot
(
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    period_year    INT         NOT NULL COMMENT '귀속 연(접수일자 기준)',
    period_month   INT         NOT NULL COMMENT '귀속 월(접수일자 기준)',

    req_date       DATE        NULL COMMENT '접수일자',
    req_cd         INT         NOT NULL COMMENT '신청번호',
    product_code   VARCHAR(20) NULL,
    product_name   VARCHAR(100) NULL,
    grade          VARCHAR(10) NULL,
    dtl_cd         INT         NOT NULL COMMENT '시행코드',
    detail_name    VARCHAR(100) NULL,
    partner_code   VARCHAR(10) NULL COMMENT '거래처코드(신청에서 온다 — 마스터에 없는 코드가 실재한다)',
    partner_name   VARCHAR(100) NULL COMMENT '거래처명(마스터에 없으면 빈다)',

    material_qty   BIGINT      NOT NULL DEFAULT 0,
    paper_qty      BIGINT      NOT NULL DEFAULT 0,
    paper_amount   BIGINT      NOT NULL DEFAULT 0,
    omr_qty        BIGINT      NOT NULL DEFAULT 0,
    omr_amount     BIGINT      NOT NULL DEFAULT 0,
    etc_qty        BIGINT      NOT NULL DEFAULT 0,
    etc_amount     BIGINT      NOT NULL DEFAULT 0,
    inwon          INT         NOT NULL DEFAULT 0 COMMENT '인원(신청 단위 1회 산정)',
    basic_amount   BIGINT      NOT NULL DEFAULT 0,
    trade_amount   BIGINT      NOT NULL DEFAULT 0,
    total_amount   BIGINT      NOT NULL DEFAULT 0,

    apply_gn       VARCHAR(5)  NULL COMMENT '구분(S=일반/A=사고). 걸러 저장하지 않고 값으로 담는다',
    canceled       BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '발송 후 취소분 여부',

    computed_at    DATETIME    NOT NULL COMMENT '굳힌 시각 — 이 숫자가 언제 기준인지',
    computed_by    VARCHAR(50) NULL,
    CONSTRAINT uq_logis_snapshot UNIQUE (period_year, period_month, req_cd, dtl_cd, product_code, grade)
) COMMENT '마감월 물류작업비 확정분 — 단가가 바뀌어도 이 값은 안 바뀐다';

CREATE INDEX ix_logis_snapshot_period ON logis_cost_snapshot (period_year, period_month);
