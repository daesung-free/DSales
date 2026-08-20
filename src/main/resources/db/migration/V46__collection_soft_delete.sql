-- 수금 수정/삭제(23p "CRUD 전체 가능 화면"). 근거: 정본 23p 핵심 요구사항.
--
-- ★레거시는 삭제가 막혀 있었다
--   수금관리.vb:356의 {@code DELETE amtData ...} 가 통째로 주석 처리돼 있다.
--   정본이 "CRUD 전체"를 요구하므로 우리는 연다. 단 물리삭제가 아니라 논리삭제다 —
--   수금은 돈이 들어온 기록이고, 지우면 그만큼 채권 잔액이 늘어난다.
--   누가 언제 지웠는지 남지 않으면 잔액이 왜 달라졌는지 설명할 수 없다(게이트규칙 논리삭제 표준).
--
-- ‼️삭제분은 채권 계산에서 반드시 빠져야 한다
--   엔티티에 @SQLRestriction("deleted_at is null")을 걸면 JPQL은 자동으로 걸러지지만
--   **네이티브 쿼리는 걸러지지 않는다**. CollectionRepository.sumByPartner(미수금현황·이월 스냅샷의
--   수금 합계)가 네이티브라, 거기엔 조건을 손으로 넣었다. 빠뜨리면 지운 수금이 계속 채권을
--   깎아 잔액이 조용히 틀어진다.

ALTER TABLE collection
    ADD COLUMN deleted_at DATETIME(6) NULL COMMENT '논리삭제 시각. 채권 계산에서 제외된다',
    ADD COLUMN deleted_by VARCHAR(50) NULL COMMENT '삭제자';

-- 활성행만 훑는 조회가 대부분이다
CREATE INDEX ix_collection_deleted ON collection (deleted_at, coll_date);
