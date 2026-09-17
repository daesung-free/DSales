-- 위탁 미결 전량 반품 시 500. 근거: 프론트 회신(2026-09-17) ②.
--
-- 재현: 2부 위탁출고 → 1부 반품(정상) → 나머지 1부 반품 → **500 INTERNAL_ERROR**.
--       미결 총수량이 0이 되는 반품만 깨진다. 부분 반품은 멀쩡하다.
--
-- ★원인: V1의 `CHECK (total_qty > 0)`.
--   반품은 total_qty 와 remaining_qty 를 함께 깎는다(V51 주석 — total_qty는 "아직 살아 있는 출고량").
--   전량 반품이면 그 값이 0이 되는데, 제약이 0을 막아 DB 예외가 그대로 500으로 새어 나갔다.
--
-- ★0은 정상 상태다.
--   "전부 반품돼 남은 게 없는 미결"이고, 불변식(total = settled + remaining)도 0 = 0 + 0 으로 성립한다.
--   original_qty 에 처음 나간 양이 남아 있어 이력도 잃지 않는다(V51).
--
-- ‼️음수까지 열지는 않는다. 0은 "다 돌아왔다"이고 음수는 "나간 것보다 더 돌아왔다"라
--   뜻이 다르다 — 그건 여전히 막아야 할 오입력이다.
--   (초과정산 ck_consign_over 는 발주처 확정으로 V56에서 걷어냈다. 이건 그것과 다른 축이다.)

ALTER TABLE consignment_out DROP CHECK consignment_out_chk_1;

ALTER TABLE consignment_out ADD CONSTRAINT ck_consign_total_nonneg CHECK (total_qty >= 0);
