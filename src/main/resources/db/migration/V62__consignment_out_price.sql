-- 위탁 미결에 **출고 시점 단가**를 남긴다.
-- 근거: 개발팀 점검(2026-09-09) P0-2 —
--   "거래처를 선택하고 [조회] 하면 그리드가 뜨지 않습니다. 응답에 정가·공급률·할인액이 없어
--    정산 공급가액을 만들 수 없습니다. 위탁 미결정산 흐름 전체가 막힙니다."
--
-- ★왜 저장하는가(조회할 때 도서 마스터에서 끌어오지 않는가)
--   발주처 확정(2026-08-05 §2.2) — "공급률은 **원 출고건 값**을 기준으로 표시하되,
--   담당자가 필요 시 수정 가능". 조회 시점에 마스터에서 끌어오면, 출고 뒤 공급률이 바뀌었을 때
--   **미결 정산 금액이 조용히 달라진다**. 나간 물건의 조건은 나갈 때 박혀야 한다.
--
-- ★NULL 허용인 이유
--   이미 쌓인 미결에는 이 값이 없다. NOT NULL로 두면 마이그레이션이 임의값을 지어내야 하는데,
--   그 값이 정산 금액이 되어 장부에 남는다. 비어 있는 것은 비어 있다고 두고,
--   아래에서 **도서 마스터 기준으로만** 채운다(근사값임을 알고 넣는다).

ALTER TABLE consignment_out
    ADD COLUMN unit_price      INT NULL COMMENT '출고 시점 정가(원). 미결 정산 공급가액의 바탕',
    ADD COLUMN supply_rate     INT NULL COMMENT '출고 시점 공급률(%). 원 출고건 값 — 정산 화면에 표시',
    ADD COLUMN discount_amount INT NULL COMMENT '출고 시점 할인액(원). 있으면 공급률 대신 금액에 쓰인다';

-- 기존 행 백필. ⚠️거래처×대분류 매핑이 아니라 **도서 기본값**으로만 채운다 —
--   출고 당시 어떤 매핑이 적용됐는지는 남아 있지 않아 되살릴 수 없다. 지어내는 대신
--   가장 단순하고 설명 가능한 값을 넣고, 담당자가 정산 시 고칠 수 있게 한다.
UPDATE consignment_out co
    JOIN products p ON p.id = co.product_id
SET co.unit_price  = COALESCE(co.unit_price, p.price),
    co.supply_rate = COALESCE(co.supply_rate, p.supply_rate)
WHERE co.unit_price IS NULL OR co.supply_rate IS NULL;
