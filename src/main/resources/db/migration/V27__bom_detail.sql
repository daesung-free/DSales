-- 33p 도서관리 세트구성(BOM) 상세 탭 필드. 근거: 요구사항정의서 1탭 33p.
--   요구 필드: 구성회차 · 시행예정일 · 분리포장여부 · 자재코드/명 · 자재구분 · 세트당 소요수량 · 물류비용 연계
--   기존 보유: 자재코드/명(child_product_id) · 세트당 소요수량(ratio)
--   이번 추가: 구성회차 · 시행예정일 · 분리포장여부 · 자재구분 · 물류비용 연계
--
-- 자재구분(시험지/해설지/OMR/라벨)은 36p 물류비용등록의 '작업구분'과 1:1 대응된다(정본 33p·36p).
-- 이 컬럼이 없으면 9p 조립 시 물류작업비 연동이 구조적으로 불가능하다 — 매칭할 축 자체가 없어서다.
-- (조립 시 자동으로 붙일지 여부 = 이슈#35, 발주처 미확정. 그 결정과 무관하게 이 필드들은 선행 필요.)

ALTER TABLE bom_items
    ADD COLUMN round         INT         NOT NULL DEFAULT 0
        COMMENT '구성회차(0=회차 구분 없음). 같은 자재가 회차별로 반복 등록될 수 있다',
    ADD COLUMN exam_date     DATE        NULL COMMENT '시행예정일(회차별)',
    ADD COLUMN separate_pack BOOLEAN     NOT NULL DEFAULT FALSE COMMENT '분리포장여부',
    ADD COLUMN material_type VARCHAR(20) NULL
        COMMENT '자재구분 EXAM_PAPER(시험지)/ANSWER_SHEET(해설지)/OMR/LABEL(라벨)/ETC',
    ADD COLUMN pack_type     INT         NULL
        COMMENT '물류비용 연계 — 물류비용등록(36p) 작업구분(PACKTYPE). 3=개별봉투(SET)';

-- 유니크 재정의: 회차별 등록이 핵심이라 같은 (완제품, 자재)가 회차마다 반복된다.
-- round를 키에 넣지 않으면 2회차 시험지를 등록하는 순간 1회차와 충돌한다.
ALTER TABLE bom_items DROP INDEX uq_bom_items;
ALTER TABLE bom_items
    ADD CONSTRAINT uq_bom_items UNIQUE (parent_product_id, round, child_product_id, del_key);

CREATE INDEX ix_bom_items_material ON bom_items (material_type);
