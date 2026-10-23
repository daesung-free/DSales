-- 마스터 동시 수정 충돌 감지(낙관적 잠금).
--
-- 근거: 프론트 실측 2026-10-22 ⑤ — 거래처·도서를 두 사람이 각각 저장하면
--   나중 저장이 **앞사람이 고친 칸까지 옛 값으로 되돌린다**.
--   거래처 수정은 전체 교체라 특히 위험하다(실제로 부산지사 내용을 날린 적이 있다).
--
-- ‼️컬럼만 넣는 것으로는 아무것도 못 막는다. 요청마다 새로 읽으면 버전이 늘 일치하기 때문이다.
--   **클라이언트가 읽은 버전을 되돌려 보내야** 충돌이 드러난다 —
--   그래서 응답에 version 을 싣고, 수정 요청에서 받아 비교한다(없으면 예전처럼 통과).
ALTER TABLE partners ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금';
ALTER TABLE products ADD COLUMN version BIGINT NOT NULL DEFAULT 0 COMMENT '낙관적 잠금';
