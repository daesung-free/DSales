-- 위탁 미결에 '반품수량' 컬럼. 근거: 발주처 회신 2026-08-21
-- 「위탁출고 반품 Case1/Case2 + "반품수량" 컬럼」.
--
-- ★지금은 반품한 흔적이 남지 않는다
--   위탁 반품(returnUnsold)은 불변식 total = settled + remaining 을 지키려고
--   total_qty와 remaining_qty를 함께 깎는다. 그래서 반품 뒤에 보면
--   "원래 얼마 나갔는지"도, "얼마가 반품됐는지"도 알 수 없다 — 처음부터 그만큼만
--   나간 것처럼 보인다.
--
-- ★두 칸을 더한다
--     original_qty : 처음 출고한 수량(불변). 반품해도 줄지 않는다.
--     returned_qty : 반품 누적.
--   그러면 화면이 이렇게 읽는다 —
--     원출고 = 정산 + 반품 + 미결잔여
--   기존 total_qty는 그대로 둔다(불변식 total = settled + remaining 유지).
--   즉 total_qty는 "아직 살아 있는 출고량", original_qty는 "처음 나간 양"이다.
--
-- 기존 행은 반품 이력이 없으므로 original_qty = total_qty로 채운다(반품 0).

ALTER TABLE consignment_out
    ADD COLUMN original_qty INT NOT NULL DEFAULT 0
        COMMENT '처음 출고한 수량(불변). 반품해도 줄지 않는다 — total_qty와 달리 원본을 보존',
    ADD COLUMN returned_qty INT NOT NULL DEFAULT 0
        COMMENT '반품 누적. 원출고 = 정산 + 반품 + 미결잔여';

UPDATE consignment_out SET original_qty = total_qty WHERE original_qty = 0;
