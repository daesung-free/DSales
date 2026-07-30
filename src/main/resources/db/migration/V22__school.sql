-- 학교관리 마스터(35p, 발주처 요청). 근거: 요구사항 35p + 레거시 DSLab.schData + DSRE tbl_school_info/hakwon_info.
-- 학교코드=거래처코드 강제 동기화(동일값). 거래처구분·학교/학원구분은 매출프로그램 직접입력(DSRE 동기화 대상 아님).
-- DSRE '가져오기'(중복 동기화 upsert)는 후속(조건부, DSRE 연동).
CREATE TABLE schools (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    school_code    VARCHAR(20)  NOT NULL UNIQUE COMMENT '학교코드(=거래처코드 동일값, DSRE MGR_CD)',
    cust_code      VARCHAR(20)  NULL COMMENT '거래처코드(partner.code 매핑)',
    cust_name      VARCHAR(100) NULL COMMENT '거래처명(표시용, schData.custName)',
    city           VARCHAR(50)  NULL COMMENT '도시(schData.custCity)',
    region         VARCHAR(50)  NULL COMMENT '지역(관할, schData.custLoc)',
    school_name    VARCHAR(100) NULL COMMENT '학교/학원명(schData.schName / SCH_NM)',
    is_school      BOOLEAN      NOT NULL DEFAULT TRUE COMMENT '학교 Y/N(schData.isSchool)',
    school_type    VARCHAR(10)  NOT NULL DEFAULT 'SCHOOL' COMMENT '학교/학원구분 SCHOOL/HAKWON(매출프로그램 직접입력)',
    client_category VARCHAR(30) NULL COMMENT '거래처구분(특약점/기타학원/B2B/대성/자사몰 등, 매출프로그램 직접입력)',
    memo           VARCHAR(500) NULL,
    created_at     DATETIME     NOT NULL,
    created_by     VARCHAR(50)  NULL,
    updated_at     DATETIME     NULL,
    updated_by     VARCHAR(50)  NULL
) COMMENT '학교/학원 마스터(35p)';

CREATE INDEX idx_schools_cust_code ON schools (cust_code);
