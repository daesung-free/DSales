-- 거래처별 단가·노출 매핑(34p) 축 교체: 도서×거래처 → 거래처×대분류.
--
-- ★ 무엇이 틀렸나
--   V19에서 이 매핑을 product_partner_price(도서 × 거래처)로 만들었는데, 정본 34p는 처음부터
--   "거래처별로 **상품군(대분류)마다** 공급률·노출여부를 사전 설정"이었다.
--   같은 문서의 예시가 축을 못박고 있다 — "특약점 D모의고사 75%, 교재 60%".
--   (발주처 회신 2026-08-20으로 이 예시의 'D모의고사'는 대분류 '기타고사'로 정정됐다.)
--   도서 단위로 두면 담당자가 도서 한 권마다 거래처 전부를 깔아야 하고, 신간이 들어올 때마다
--   같은 일을 반복해야 한다. 실제 운영은 상품군 단위 대표값이다.
--
-- ★ 옛 표는 남기지 않고 지운다
--   두 축을 다 두면 매출등록 공급률 자동조회가 "무엇을 먼저 보나"라는 규칙을 하나 더 갖게 된다.
--   그게 레거시의 실패 방식이었다(화면마다 다른 계산식 → 같은 도서가 화면마다 값이 다름).
--   실사용 데이터는 아직 없고 테스트 픽스처뿐이라 지금이 갈아엎을 수 있는 마지막 시점이다.
--
-- ★ 정본 34p 데이터 항목: 거래처코드·거래처명·거래처구분·Web게시·공급률·할인액·사용여부
--   거래처코드/명/구분은 partners에서 조인해 오고, 나머지 넷이 이 표의 컬럼이다.
--   ⚠️할인액은 값만 보관한다 — 금액 계산에 반영하는 것은 별도 작업이다(레거시 공식
--     if(할인액>0, 정가-할인액, 정가×공급률/100)은 단가 산출식이라 Sale의 금액 단일소스를 함께 고쳐야 한다).

CREATE TABLE partner_supply_rate
(
    partner_id      BIGINT      NOT NULL COMMENT '거래처',
    major_category  VARCHAR(20) NOT NULL COMMENT '대분류(MajorCategory). 같은 거래처도 대분류마다 다른 공급률',
    supply_rate     INT         NULL COMMENT '공급률(%). 단가 = 도서 정가 × 공급률/100',
    discount_amount INT         NULL COMMENT '할인액(원). 정본 34p 항목 — 금액 계산 반영은 후속',
    web_visible     BOOLEAN     NOT NULL DEFAULT TRUE COMMENT 'Web게시여부(신청사이트 노출 개별 제어)',
    use_yn          BOOLEAN     NOT NULL DEFAULT TRUE COMMENT '사용여부',
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    created_at      DATETIME    NOT NULL,
    created_by      VARCHAR(50) NULL,
    updated_at      DATETIME    NULL,
    updated_by      VARCHAR(50) NULL,
    deleted_at      DATETIME(6) NULL,
    deleted_by      VARCHAR(50) NULL,
    -- 논리삭제 표준(V25와 같은 방식): 활성행끼리만 유일성을 강제해, 지웠다가 같은 조합으로 재등록할 수 있다.
    del_key         DATETIME(6) GENERATED ALWAYS AS (COALESCE(deleted_at, '1970-01-01 00:00:00.000000')) STORED,
    CONSTRAINT uq_partner_supply_rate UNIQUE (partner_id, major_category, del_key),
    CONSTRAINT fk_psr_partner FOREIGN KEY (partner_id) REFERENCES partners (id)
) COMMENT '거래처별 대분류 공급률·노출(34p)';

CREATE INDEX ix_psr_partner ON partner_supply_rate (partner_id, deleted_at);
CREATE INDEX ix_psr_major ON partner_supply_rate (major_category, deleted_at);

-- 잘못된 축의 표를 제거. 인덱스·FK는 테이블과 함께 사라진다.
DROP TABLE product_partner_price;
