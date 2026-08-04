-- 개인회원관리(구 IC). 근거: 레거시 DSLab.personData + 개인회원관리.vb(512줄).
-- 정본 3탭 "매핑 누락 - 확인 필요 | 개인회원관리(구 IC)" 항목 — 레거시 소스·실데이터(145행) 모두 확보돼
-- 이관 가능. 온라인 강의/교재를 개인이 직접 결제한 건의 수취인·배송지 관리 화면이다.
--
-- 레거시 대비 조정:
--  · idx(IDENTITY) → id
--  · inputDate/payDate가 varchar('20170607_1557182' 형태)라 DATETIME으로 정규화(Phase6 날짜 정규화 방침)
--  · 물리 DELETE 하던 화면이라(개인회원관리.vb:500) 논리삭제 표준 적용

CREATE TABLE person_member (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    fiscal_year VARCHAR(4)   NULL COMMENT '연도(personData.year)',
    input_date  DATETIME     NULL COMMENT '등록일(personData.inputDate)',
    pay_date    DATETIME     NULL COMMENT '결제일(personData.payDate) — 조회 기본 기간축',
    student_id  VARCHAR(20)  NOT NULL COMMENT '학생ID(stID)',
    student_name VARCHAR(20) NOT NULL COMMENT '학생이름(stName)',
    goods_code  VARCHAR(50)  NULL COMMENT '상품코드(gdCode)',
    goods_name  VARCHAR(50)  NULL COMMENT '상품명(gdName)',
    post        VARCHAR(6)   NULL COMMENT '우편번호',
    addr1       VARCHAR(100) NULL COMMENT '주소1',
    addr2       VARCHAR(100) NULL COMMENT '주소2',
    receiver    VARCHAR(20)  NULL COMMENT '수취인명(personData.name)',
    tel1        VARCHAR(20)  NULL COMMENT '연락처1',
    tel2        VARCHAR(20)  NULL COMMENT '연락처2',
    memo        VARCHAR(500) NULL,
    manager     VARCHAR(30)  NULL COMMENT '관리(담당)',
    deleted_at  DATETIME(6)  NULL,
    deleted_by  VARCHAR(50)  NULL,
    created_at  DATETIME     NOT NULL,
    created_by  VARCHAR(50)  NULL,
    updated_at  DATETIME     NULL,
    updated_by  VARCHAR(50)  NULL
) COMMENT '개인회원(구 IC) — 개인 결제 건의 수취인·배송지';

CREATE INDEX ix_person_member_pay ON person_member (pay_date);
CREATE INDEX ix_person_member_student ON person_member (student_id);
CREATE INDEX ix_person_member_deleted ON person_member (deleted_at);
