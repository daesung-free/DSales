-- 학교관리 DSRE 동기화 방식 변경(35p 발주처 확정): 전체삭제 후 재수입 → 보존형 병합(upsert).
-- 근거: DSRE2 tbl_cust_ref(지사↔학교/학원 매핑, UNIQUE (CUST_CD, MGR_GN, MGR_CD)).
--
-- 정정 사항: V22는 "학교코드=거래처코드 동일값"을 전제로 school_code 단독 UNIQUE였으나,
--   레거시 원본 DSLab.schData는 schCode(PK)와 custCode가 별개 컬럼이고,
--   DSRE2 tbl_cust_ref도 (거래처코드, 학교코드) 복합 유일이다. 발주처 지시 매칭키와도 일치.
--   → 동일값 전제를 폐기하고 복합키로 전환한다.

-- 1) 복합 유일키 전환 -------------------------------------------------------
-- cust_code가 NULL이면 MySQL 유니크가 중복을 허용해 매칭키가 무력화된다 → NOT NULL + 빈문자 기본값.
UPDATE schools SET cust_code = '' WHERE cust_code IS NULL;
ALTER TABLE schools MODIFY COLUMN cust_code VARCHAR(20) NOT NULL DEFAULT ''
    COMMENT '거래처코드(partner.code / DSRE CUST_CD) — 학교코드와 함께 동기화 매칭키';
ALTER TABLE schools DROP INDEX school_code;
ALTER TABLE schools ADD CONSTRAINT uq_schools_cust_school UNIQUE (cust_code, school_code);

-- 2) 동기화 보존 장치 -------------------------------------------------------
-- source: 이 행이 DSRE에서 온 것인지 매출프로그램 전용인지. 전용 행(MANUAL)은 동기화가 건드리지 않는다.
--         (문서 예시: 거래처코드 20005 / 온라인스터디카페 — DSRE2에 없는 매출프로그램 전용 거래처)
ALTER TABLE schools ADD COLUMN source VARCHAR(10) NOT NULL DEFAULT 'MANUAL'
    COMMENT '출처 DSRE/MANUAL — MANUAL은 동기화 대상에서 제외(전용 데이터 보호)';

-- active: DSRE2에서 사라진 행은 물리삭제하지 않고 미사용 처리(과거 매출 이력과 끊기면 안 됨).
ALTER TABLE schools ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE
    COMMENT '사용여부 — DSRE2에서 사라진 건 false(미사용). 삭제하지 않는다';

CREATE INDEX idx_schools_source_active ON schools (source, active);
