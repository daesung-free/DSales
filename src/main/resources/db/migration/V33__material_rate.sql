-- 자재 단가 마스터 + 세트 조립 작업비. 근거: 발주처 확정(2026-08-05 회신)
-- "세트 조립 작업비는 자동 계산이 맞습니다. 물류비용등록에 등록된 단가 기준으로 시스템이 자동 계산하고,
--  물류팀은 이 자동 계산된 내역을 다운로드하여 별도로 가공이 필요한 부분은 수작업을 하여 작업비 정산을 요청".
--
-- 단가 축: 정본 구분값정리 10.물류비용등록 — 자재별 단가(시험지/OMR/단행본/라벨)가
--          33p BOM 탭의 '자재구분'과 매칭된다. 작업구분(반별봉투/개별봉투/개별봉투SET)에 따라 단가가 달라진다.
--          → 단가 키 = (자재구분, 작업구분).
--
-- ★계산 시점에 확정해 저장한다(조회 시 재계산 아님).
--   단가표나 BOM 구성이 나중에 바뀌어도 이미 끝난 작업의 비용이 소급해서 변하면 안 된다 —
--   물류팀이 이미 그 금액으로 정산을 요청했을 수 있다.

CREATE TABLE material_rate (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    material_type VARCHAR(20) NOT NULL
        COMMENT '자재구분 EXAM_PAPER(시험지)/ANSWER_SHEET(해설지)/OMR/LABEL(라벨)/ETC',
    pack_type     INT         NOT NULL DEFAULT 0
        COMMENT '작업구분(물류비용등록 PACKTYPE). 0=구분 없음(공통 단가)',
    unit_rate     INT         NOT NULL COMMENT '자재 1개당 단가(원)',
    memo          VARCHAR(200) NULL,
    created_at    DATETIME    NOT NULL,
    created_by    VARCHAR(50) NULL,
    updated_at    DATETIME    NULL,
    updated_by    VARCHAR(50) NULL,
    CONSTRAINT uq_material_rate UNIQUE (material_type, pack_type)
) COMMENT '자재구분×작업구분 단가 — 세트 조립 작업비 자동계산의 단가 소스';

-- 조립 작업비: 조립 이벤트(BOM_ASSEMBLE)에만 값이 들어간다. 해체는 포장 작업이 없어 대상 아님.
ALTER TABLE inventory_txn
    ADD COLUMN work_cost BIGINT NULL COMMENT '세트 조립 작업비(자동계산). 조립 이벤트에만 기록';

CREATE INDEX ix_inventory_txn_work_cost ON inventory_txn (txn_type, trade_date);
