-- 작업요청서 '확인' 표시. 근거: 프론트 회신(2026-09-17) B-14 —
--   "'확인' 표시는 아직 준비 중입니다 — 지금은 출력 여부로만 진행 단계를 봅니다"
--
-- ★왜 기존 completed_at 을 쓰지 않는가
--   V39 에 completed_at 이 이미 있지만 주석 그대로 "⚠️레거시에 쓰기 경로가 없다(읽기만)"이다.
--   레거시에도 값을 넣는 코드가 없어 작업결과의 '완료'는 항상 false 다.
--   거기에 우리가 '확인'을 밀어 넣으면 **레거시에서 뜻이 달랐던 칸**을 조용히 재사용하는 게 되고,
--   나중에 레거시 데이터를 대조할 때 완료인지 확인인지 가릴 수 없다. 축을 따로 둔다.
--
-- ★되돌리기는 컬럼을 추가하지 않는다
--   출력 되돌리기(printed_at → NULL)는 status_history(V31)에 남긴다.
--   "언제 처음 지시가 나갔나"를 지우는 행위라 **누가·왜**가 반드시 필요한데,
--   그건 이미 status_history 가 하는 일이다. 컬럼을 또 만들면 감사기록이 두 곳으로 갈린다.

ALTER TABLE shipment
    ADD COLUMN acknowledged_at DATETIME(6) NULL COMMENT '작업 확인 시각. 작업결과의 ''확인'' ○',
    ADD COLUMN acknowledged_by VARCHAR(50) NULL COMMENT '확인 처리자';

-- 미확인 건만 추려 보는 조회가 출력여부 필터와 같은 빈도로 쓰인다(작업 대기 리스트).
CREATE INDEX ix_shipment_ack ON shipment (trade_date, acknowledged_at);
