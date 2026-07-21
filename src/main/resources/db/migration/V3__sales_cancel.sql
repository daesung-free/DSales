-- V3 — 매출 취소(논리 취소)
-- 근거: 기획서 '취소=회계상 반품' / 레거시는 물리삭제 아님(이력 보존).
-- 원 매출을 지우지 않고 canceled 표시. (재고 복구는 주문/출고 통합 단계에서)
ALTER TABLE sales ADD COLUMN canceled    BOOLEAN   NOT NULL DEFAULT FALSE;
ALTER TABLE sales ADD COLUMN canceled_at TIMESTAMP;
