-- 거래처 ↔ DSRE 거래처코드는 **1:N 이다.** V98 이 한 칸으로 둔 것을 매핑표로 고친다.
--
-- ★ 운영에서 드러난 사실 (2026-10-23, DSRE 214행 실측)
--   매출코드 하나에 DSRE 거래처코드가 여럿 달린 곳이 **5곳** 있다.
--
--     A30D201 → 90001(학교 263) · 99001(23)
--     G10A231 → 97752·97753·97754·97755·97756   ← 다섯 개
--     A30D222 → 98601(1)       · 98751(116)
--     E10A122 → 99700(13)      · 99991(6)
--     A30D283 → 00301(57)      · 00881(0)
--
--   V98 처럼 한 칸만 두면 동기화가 돌 때마다 **마지막에 읽힌 값으로 덮인다** —
--   실제로 2회차 실행에서 13건이 바뀌었다(비멱등).
--   그리고 어느 하나를 골라도 **나머지 코드를 쓰는 학교가 통째로 안 붙는다**
--   (최소값 채택 시 145곳. 특히 A30D222 는 116곳 쪽을 버리게 된다).
--
-- ★ 왜 표로 빼나
--   "거래처 1건에 DSRE 코드 1개"라는 내 전제가 틀렸을 뿐이다. 사실은 1:N 이고,
--   학교 조인은 **그 거래처의 코드 아무거나** 맞으면 돼야 한다.
--
-- ★ partners.dsre_code 는 남긴다
--   PDF 파일명처럼 **하나만 써야 하는 자리**가 있다(RESULT_{코드}_{거래처명}_…).
--   그 자리의 대표값으로 쓰고, 조인은 매핑표가 맡는다.
--   대표값은 동기화가 **가장 작은 코드**로 정한다 — 무엇이든 하나를 정해야
--   실행할 때마다 파일명이 달라지지 않는다.

CREATE TABLE partner_dsre_code
(
    partner_id BIGINT      NOT NULL COMMENT '거래처',
    dsre_code  VARCHAR(20) NOT NULL COMMENT 'DSRE2 tbl_cust_info.CUST_CD',
    created_at DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (partner_id, dsre_code),
    CONSTRAINT fk_pdc_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) COMMENT '거래처 ↔ DSRE 거래처코드(1:N). 학교 조인이 이 표를 탄다';

-- 학교 조회가 코드로 들어온다 — 역방향 인덱스.
-- ‼️UNIQUE 가 아니다. 같은 DSRE 코드를 여러 거래처가 쓰는지는 아직 확인되지 않았고,
--   UNIQUE 로 걸면 그런 데이터가 나타나는 순간 동기화가 통째로 실패한다.
CREATE INDEX idx_pdc_dsre_code ON partner_dsre_code (dsre_code);

-- V98 로 채워 둔 값을 옮겨 둔다(동기화를 다시 돌리면 어차피 전량 다시 채워진다).
INSERT INTO partner_dsre_code (partner_id, dsre_code)
SELECT id, dsre_code FROM partners
WHERE dsre_code IS NOT NULL AND dsre_code <> '';
