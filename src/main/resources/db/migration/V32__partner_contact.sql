-- 거래처관리(30p) 누락 필드 보완. 근거: 레거시 거래처관리.vb 조회 컬럼 전수 + 정본 구분값정리 4.거래처관리
-- ("기존 항목 그대로 유지"로 명시된 항목들).
--
-- 레거시 화면 컬럼 26개 중 우리에게 없던 9개를 채운다. 컬럼명은 레거시 custData 기준.
--   bossID    사업자주민번호     tel1/tel2  연락처1·2
--   cellPhone 휴대폰번호         fax        팩스번호
--   zip       우편번호           zone2      관할지역
--   startDate 등록일             endDate    만료일
--
-- ★endDate가 특히 중요하다 — 레거시 조회는 `len(endDate)=0`(만료 안 됨)만 기본 노출하고,
--   '만료된 거래처 포함' 체크 시 전체를 보여준다. 이 컬럼이 없으면 그 필터를 만들 수 없다.
--
-- 이미 보유: code · name(거래처명) · name1(신고명) · biz_no · biz_status(업종) · biz_item(업태)
--            · boss_name(사업자명) · email1·2 · addr1(기본주소) · addr2(상세주소)
--            · region(지역) · client_category(거래처구분) · 담보 3종 · city_name(도시, DSRE 유래)

ALTER TABLE partners
    ADD COLUMN boss_id    VARCHAR(20)  NULL COMMENT '사업자주민번호(custData.bossID)',
    ADD COLUMN tel1       VARCHAR(20)  NULL COMMENT '연락처1',
    ADD COLUMN tel2       VARCHAR(20)  NULL COMMENT '연락처2',
    ADD COLUMN cell_phone VARCHAR(20)  NULL COMMENT '휴대폰번호',
    ADD COLUMN fax        VARCHAR(20)  NULL COMMENT '팩스번호',
    ADD COLUMN zip        VARCHAR(10)  NULL COMMENT '우편번호',
    ADD COLUMN zone2      VARCHAR(200) NULL COMMENT '관할지역(custData.zone2). region=지역(zone1)과 별개 축',
    ADD COLUMN start_date DATE         NULL COMMENT '등록일(거래 시작)',
    ADD COLUMN end_date   DATE         NULL COMMENT '만료일 — NULL이면 거래중. 조회 기본은 거래중만 노출';

-- '만료된 거래처 포함' 체크박스가 끄기/켜기로 전체를 훑으므로 인덱스를 둔다.
CREATE INDEX ix_partners_end_date ON partners (end_date);
