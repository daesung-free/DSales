-- 대시보드 실적 스냅샷. 근거: 발주처 확정(자료요청서 3-2 아) — "③ 하루 1회 갱신(예: 매일 새벽)".
--
-- 대시보드는 매출 전체를 월별로 합산하는 화면이라 조회할 때마다 계산하면 느려진다.
-- 발주처가 "그 정도 신선도면 충분하다"고 확정해, 새벽 배치가 미리 계산해 여기 담는다.
--
-- ★그래도 조회는 스냅샷이 없으면 실시간으로 계산한다(폴백).
--   배치가 안 돌았거나 실패한 날 화면이 통째로 비면 "느린 것"보다 나쁘다.
--   대신 응답에 기준시각(computed_at)을 실어, 지금 보는 숫자가 언제 것인지 드러낸다.
--
-- 축은 sales_target(V35)과 같다 — 목표와 실적을 같은 키로 맞춰야 달성률이 나온다.

CREATE TABLE dashboard_snapshot (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    fiscal_year  INT          NOT NULL COMMENT '연도',
    month        INT          NOT NULL COMMENT '월(1~12)',
    scope        VARCHAR(20)  NOT NULL COMMENT '집계 축 COMPANY(전사)/PRODUCT(상품)',
    product_id   BIGINT       NULL     COMMENT 'scope=PRODUCT일 때 상품 id',
    net_sales    BIGINT       NOT NULL COMMENT '순매출(매출−반품). 취소 제외',
    computed_at  DATETIME(6)  NOT NULL COMMENT '이 값을 계산한 시각 — 화면에 "언제 기준"인지 보여준다',
    created_at   DATETIME     NOT NULL,
    created_by   VARCHAR(50)  NULL,
    updated_at   DATETIME     NULL,
    updated_by   VARCHAR(50)  NULL,
    CONSTRAINT chk_dashboard_snapshot_month CHECK (month BETWEEN 1 AND 12)
) COMMENT '대시보드 월별 실적 스냅샷(일 1회 갱신)';

-- NULL은 서로 다르게 취급돼 중복이 뚫리므로 COALESCE로 정규화한다(sales_target과 같은 방식).
CREATE UNIQUE INDEX ux_dashboard_snapshot
    ON dashboard_snapshot (fiscal_year, month, scope, (COALESCE(product_id, -1)));

CREATE INDEX ix_dashboard_snapshot_year ON dashboard_snapshot (fiscal_year, scope);
