-- 마감 해제 사유를 따로 남긴다. 근거: 프론트 회신(2026-09-17) ⑪ —
--   "해제 memo가 마감 memo로 덮입니다. 해제는 '왜 열었는지'가 더 중요한 기록입니다."
--
-- ★지금도 잃지는 않았다 — status_history 에는 남는다.
--   다만 `period_lock.memo` 한 칸을 확정·해제가 번갈아 쓰다 보니
--   마감 목록 화면에는 **마지막에 쓴 쪽만** 보였다. 해제했다가 다시 마감하면 해제 사유가 사라진다.
--
-- ★두 칸으로 나눈다. 같은 칸을 돌려쓰면 어느 쪽 사유인지 구분할 수 없다.
--   memo        = 마감 확정 사유(기존 값 유지)
--   unlock_memo = 마감 해제 사유
--
-- ‼️해제 후 다시 마감해도 unlock_memo 는 지우지 않는다.
--   "이 달은 한 번 열렸었다"는 사실 자체가 감사 대상이다 — 덮으면 그게 사라진다.

ALTER TABLE period_lock ADD COLUMN unlock_memo VARCHAR(500) NULL
    COMMENT '마감 해제 사유. 확정 사유(memo)와 따로 둔다 — 한 칸을 돌려쓰면 마지막 것만 남는다';

ALTER TABLE period_lock ADD COLUMN unlocked_by VARCHAR(50) NULL
    COMMENT '마지막으로 해제한 사람';

ALTER TABLE period_lock ADD COLUMN unlocked_at DATETIME NULL
    COMMENT '마지막으로 해제한 시각';
