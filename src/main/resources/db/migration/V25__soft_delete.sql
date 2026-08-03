-- 논리삭제(soft delete) 표준 도입.
-- 근거: 게이트규칙 "반드시 지킬 것 — 논리삭제 표준. 물리 DELETE는 예외 승인 절차로만"(보안심사 직결).
--
-- 적용 대상 = 실제로 물리 DELETE가 돌던 2곳:
--   bom_items              — BOM 재등록 시 기존 구성 전량 삭제(ProductService.registerBom)
--   product_partner_price  — 거래처별 단가 매핑 삭제 API(DELETE .../partner-prices/{partnerId})
--
-- 적용 제외(사유 명시):
--   products               — 삭제 API가 이미 use_yn=false 논리삭제. 물리 DELETE 없음.
--   receivable_carryforward— 회계연도 이월 스냅샷의 재생성(idempotent). '삭제'가 아니라 파생데이터
--                            재계산이라 원장이 아님 → 물리삭제 유지가 정상. 예외 승인 대상으로 기록.
--   DSRE2 tbl_logis_cost   — 외부 운영 DB(분리 유지 확정). 우리 표준 적용 범위 밖.

-- 1) 삭제 감사 컬럼 --------------------------------------------------------
ALTER TABLE bom_items
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(50) NULL;

ALTER TABLE product_partner_price
    ADD COLUMN deleted_at DATETIME(6) NULL,
    ADD COLUMN deleted_by VARCHAR(50) NULL;

-- 2) 유니크 재정의 ---------------------------------------------------------
-- 삭제행이 유니크 키를 계속 점유하면 "지웠다가 같은 조합으로 재등록"이 막힌다.
-- BOM은 재등록마다 같은 (parent, child)를 다시 넣으므로 이 처리 없이는 즉시 깨진다.
--   del_key: 활성행 = 고정 sentinel  → 활성행끼리만 유일성 강제
--            삭제행 = 삭제시각(마이크로초) → 서로 달라 중복 허용
ALTER TABLE bom_items
    ADD COLUMN del_key DATETIME(6)
        GENERATED ALWAYS AS (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED;
ALTER TABLE bom_items DROP INDEX parent_product_id;
ALTER TABLE bom_items
    ADD CONSTRAINT uq_bom_items UNIQUE (parent_product_id, child_product_id, del_key);

ALTER TABLE product_partner_price
    ADD COLUMN del_key DATETIME(6)
        GENERATED ALWAYS AS (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED;
ALTER TABLE product_partner_price DROP INDEX uq_product_partner;
ALTER TABLE product_partner_price
    ADD CONSTRAINT uq_product_partner UNIQUE (product_id, partner_id, del_key);

-- 3) 활성행 조회 인덱스 -----------------------------------------------------
CREATE INDEX ix_bom_items_deleted ON bom_items (deleted_at);
CREATE INDEX ix_ppp_deleted ON product_partner_price (deleted_at);
