-- 거래처별 단가·노출 매핑(도서관리 3번째 탭). 근거: 요구사항 32~34p.
-- 도서×거래처 단위 공급률(→단가 파생)·노출여부. 매출등록 공급률 자동조회 기반.

CREATE TABLE product_partner_price (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id  BIGINT  NOT NULL,
    partner_id  BIGINT  NOT NULL,
    supply_rate INT     NULL,
    visible     BOOLEAN NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP NOT NULL,
    created_by  VARCHAR(50),
    updated_at  TIMESTAMP,
    updated_by  VARCHAR(50),
    CONSTRAINT uq_product_partner UNIQUE (product_id, partner_id),
    CONSTRAINT fk_ppp_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_ppp_partner FOREIGN KEY (partner_id) REFERENCES partners(id)
);
CREATE INDEX ix_ppp_product ON product_partner_price (product_id);
