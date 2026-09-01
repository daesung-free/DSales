-- 자재 마스터 + 회차↔자재 매칭. 근거: 발주처 「도서관리·제품수불부현황 데이터 구조 보완 요청안」(2026-08-31).
--
-- ★상품은 세 단계다 —
--     세트(SET)  2026 D.ARCHIVE 국어 시즌1 (전체묶음)   단독 판매되는 최상위
--      └ 회차     1회, 2회 …                          회차 자체도 단품으로 별도 판매
--         └ 자재   시험지 · 해설지 · OMR · 라벨          회차를 구성하는 물리적 소모품
--
--   세트–회차는 이미 bom_items로 표현된다(회차가 products 행이기 때문 —
--   화면기획 보완요청안의 도서 목록이 도서코드 00=SET, 01=1회, 02=2회로 등록돼 있다).
--   새로 필요한 것은 **자재**뿐이다.
--
-- ★자재를 products에 두지 않는 이유
--   ① 자재는 팔지 않는다. 정가·공급률·매출구분·수불부노출이 전부 무의미하다.
--   ② 범용 자재(OMR·교사용라벨)는 **1건을 여러 세트·회차에 중복 매칭**해야 하는데
--      bom_items는 UNIQUE(parent, child)라 세트마다 별도 행을 만들어야 한다.
--      원문: "OMR·교사용라벨 등 범용 자재는 1건을 여러 세트·회차에 중복 매칭할 수 있어야 합니다".
--   ③ 원문이 "자재를 세트별 BOM에 매번 새로 입력하는 방식이 아니라, **별도 자재 목록에서
--      등록·관리하고 이를 각 세트의 구성회차에 선택해 매칭**하는 방식으로"라고 못 박았다.

-- 1) 자재 마스터 --------------------------------------------------------------
CREATE TABLE materials
(
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    code          VARCHAR(30) NOT NULL COMMENT '자재코드',
    name          VARCHAR(200) NOT NULL
        COMMENT '자재명. 상품명·시즌·회차 등 콘텐츠를 특정할 수 있게 등록(예: 2026_D.ARCHIVE 국어 시즌1_01회)',
    material_type VARCHAR(20) NOT NULL
        COMMENT '자재구분 PAPER(시험지)/ANSWER(해설지)/OMR/LABEL(라벨)/BOOK(단행본·책자)',
    use_yn        BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '사용여부',
    memo          VARCHAR(200) NULL,
    created_at    DATETIME    NOT NULL,
    created_by    VARCHAR(50) NULL,
    updated_at    DATETIME    NULL,
    updated_by    VARCHAR(50) NULL,
    deleted_at    DATETIME    NULL,
    deleted_by    VARCHAR(50) NULL,
    del_key       DATETIME(6) GENERATED ALWAYS AS
                      (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED,
    CONSTRAINT uq_materials_code UNIQUE (code, del_key)
) COMMENT '자재 마스터 — 세트 BOM에 매칭해 쓰는 물리적 소모품 목록';

-- ★자재구분이 핵심이다. 물류 작업비가 여기서 나온다.
--   원문: "물류 작업비는 세트구성(BOM)의 자재별 소요수량에 그 시행의 해당 자재구분 단가
--   (시험지→시험지, OMR→OMR, 단행본·책자→단행본, 라벨→라벨, **해설지→시험지**)를 곱해 계산".
--   즉 해설지는 자재 목록에서는 시험지와 별개지만, 단가는 시험지 단가를 쓴다.
CREATE INDEX ix_materials_type ON materials (material_type);

-- 2) 회차 ↔ 자재 매칭 -----------------------------------------------------------
CREATE TABLE material_bom
(
    id               BIGINT AUTO_INCREMENT PRIMARY KEY,
    set_product_id   BIGINT NOT NULL COMMENT '세트 상품(products)',
    round_product_id BIGINT NULL
        COMMENT '회차 상품(products). NULL이면 **공통** — 세트 전체에 붙는 범용 자재',
    material_id      BIGINT NOT NULL,
    qty_per_set      INT    NOT NULL CHECK (qty_per_set > 0)
        COMMENT '세트당 소요수량. 매칭 시점에 개별 입력한다',
    created_at       DATETIME    NOT NULL,
    created_by       VARCHAR(50) NULL,
    updated_at       DATETIME    NULL,
    updated_by       VARCHAR(50) NULL,
    CONSTRAINT fk_mbom_set   FOREIGN KEY (set_product_id)   REFERENCES products (id),
    CONSTRAINT fk_mbom_round FOREIGN KEY (round_product_id) REFERENCES products (id),
    CONSTRAINT fk_mbom_mat   FOREIGN KEY (material_id)      REFERENCES materials (id)
) COMMENT '세트·회차 ↔ 자재 매칭 + 세트당 소요수량';

-- ‼️UNIQUE에 round_product_id가 들어가지만 MySQL은 NULL을 서로 다른 값으로 본다.
--   즉 '공통'(NULL) 매칭은 같은 자재를 여러 번 넣어도 막히지 않는다.
--   공통 중복은 애플리케이션에서 막는다(NULL 대신 sentinel을 쓰면 FK를 못 건다).
CREATE UNIQUE INDEX uq_material_bom ON material_bom (set_product_id, round_product_id, material_id);
CREATE INDEX ix_mbom_set ON material_bom (set_product_id);
CREATE INDEX ix_mbom_mat ON material_bom (material_id);

-- 3) 물류비용 연계 필드 철회 ------------------------------------------------------
-- 원문(기존 요청 수정): "자재 목록에 물류비용 연계 항목을 두도록 요청드렸던 부분을 …
--   **별도의 '물류비용 연계' 항목을 두지 않습니다.** 물류비용등록(36p)은 시행(상품+시행명)
--   단위로 시험지/OMR/단행본/라벨 단가를 이미 개별 컬럼으로 등록해 두는 화면으로,
--   자재별로 별도 등록·매칭하는 절차는 없습니다. … 자재 마스터에 별도 비용 연계 필드는
--   필요하지 않습니다. 등록 시 **자재구분을 정확히 선택하는 것이 중요**합니다."
--
-- V27에서 bom_items.pack_type(물류비용 연계 — 작업구분 PACKTYPE 링크)을 넣었던 것을 되돌린다.
ALTER TABLE bom_items DROP COLUMN pack_type;
