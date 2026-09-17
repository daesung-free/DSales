-- 무상 세부구분(part) 축 신설. 근거: 레거시 제품수불부 무상 4칸(IC학생용·IC·M+·기타) 재현.
--
-- ★레거시에 실재하는 기능이다 — `제품수불부.vb:116~121` 의 집계식과
--   `salesData.part`(nvarchar 30) 컬럼, `UC_TabPages.vb:738` 의 그리드 '구분' 입력이 근거다.
--   정본 11p 데이터 항목에는 안 적혀 있는데, 같은 칸에 "H(교재)/I(IC)/S(기타고사) 3개 라인이
--   개별 폼에 산재 → 통합 확장 필요"라고 적혀 있다. 세 화면을 합치라는 요구지 IC 세부를
--   버리라는 요구가 아니라서, 누락으로 보고 재현한다.
--   (월마감 period_locks 도 같은 경위로 되살렸다 — 정본 일부에만 있고 개발문서엔 없었다.)
--
-- ★값: 학생용 / 교사용 / M+ / IC+ / (미지정)
--   레거시가 실제로 쓰는 값이 이 넷이다. 자유 문자열로 두면 표기가 갈려 집계가 쪼개지므로
--   길이만 제한하고 검증은 애플리케이션에서 한다(거래처구분·학년과 같은 방식).
--
-- ‼️inventory_txn 에도 같이 싣는다.
--   제품수불부는 sales 가 아니라 inventory_txn 을 집계한다. 매출에만 넣으면 수불부가 못 본다.
--   shipment_type 이 이미 같은 이유로 복사돼 있다(V4).

ALTER TABLE sales ADD COLUMN part VARCHAR(30) NULL
    COMMENT '무상 세부구분(학생용/교사용/M+/IC+). 레거시 salesData.part';

ALTER TABLE inventory_txn ADD COLUMN part VARCHAR(30) NULL
    COMMENT '무상 세부구분. 매출에서 복사 — 수불부가 이 테이블을 집계하기 때문';

-- 수불부가 (창고 × 상품 × 출고유형 × part)로 접는다. 기존 인덱스에 얹지 않고 따로 둔다 —
-- part 는 무상 건에만 차서 선택도가 낮고, 기존 조회 계획을 건드리지 않는 편이 안전하다.
CREATE INDEX ix_txn_part ON inventory_txn (part);
