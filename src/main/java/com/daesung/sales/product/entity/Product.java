package com.daesung.sales.product.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 상품. 근거: bookData + 🆕 content_type/is_set. */
@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Product extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 30)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "content_type", nullable = false, length = 10)
    private ContentType contentType;

    @Column(name = "is_set", nullable = false)
    private boolean set;

    private Integer price;

    @Column(name = "tax_free", nullable = false)
    private boolean taxFree;

    @Column(length = 5)
    private String grade;

    /** 분류코드(계층, 첫 글자=대분류). 매출액명세서 rollup 축. 근거: 레거시 catCode. */
    @Column(name = "cat_code", length = 20)
    private String catCode;

    /** 분류명. 근거: 레거시 catName. */
    @Column(name = "cat_name", length = 100)
    private String catName;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    /** 매출구분(매출액정리·순매출조회 집계기준). 근거: 32p 도서관리. */
    @Column(name = "sales_division", length = 30)
    private String salesDivision;

    /** 상품년도(예: 2026). 근거: 32p 도서관리(레거시 bookData 부재→신규). */
    @Column(name = "product_year")
    private Integer productYear;

    /** 상품구분(레거시 bookData.type 원시값). contentType(콘텐츠구분 SELF/EXTERNAL)과 별개 분류. 근거: 32p 도서관리. */
    @Column(name = "product_type", length = 30)
    private String productType;

    /**
     * 기본 공급률(%). 근거: 정본 구분값정리 [6.도서관리-1.기본정보] "정가 / 공급률 = 판매가 계산 기준".
     *
     * <p>거래처별 매핑({@code product_partner_price})이 없을 때 쓰는 바탕값이다.
     * 매출등록 자동적용 우선순위는 <b>입력값 &gt; 거래처별 매핑 &gt; 이 값</b>.
     */
    @Column(name = "supply_rate")
    private Integer supplyRate;

    /** 수불부노출 여부(제품수불부 집계 포함). 기본 true. */
    @Column(name = "ledger_visible", nullable = false)
    private boolean ledgerVisible = true;

    /** Web/신청사이트 게시 여부. 기본 false. */
    @Column(name = "web_visible", nullable = false)
    private boolean webVisible = false;

    /**
     * 재고관리 여부. 기본 true. false면 매출 시 재고 차감·재고이벤트 없음(수불부·재고 대상 아님).
     * 근거: 레거시 모의고사는 invenData(재고장부) 미기록·제품수불부 없음 = 인원 기반 종량제 매출.
     */
    @Column(name = "stock_managed", nullable = false)
    private boolean stockManaged = true;

    public static Product create(String code, String name, ContentType contentType, boolean set,
                                 Integer price, boolean taxFree, String grade,
                                 String catCode, String catName, boolean useYn,
                                 String salesDivision, boolean ledgerVisible, boolean webVisible,
                                 boolean stockManaged) {
        Product p = new Product();
        p.code = code;
        p.name = name;
        p.contentType = contentType;
        p.set = set;
        p.price = price;
        p.taxFree = taxFree;
        p.grade = grade;
        p.catCode = catCode;
        p.catName = catName;
        p.useYn = useYn;
        p.salesDivision = salesDivision;
        p.ledgerVisible = ledgerVisible;
        p.webVisible = webVisible;
        p.stockManaged = stockManaged;
        return p;
    }

    /** 32p 확장 필드(상품년도·상품구분·기본공급률) 설정 — 등록·수정 시 사용. */
    public void applyExtra(Integer productYear, String productType, Integer supplyRate) {
        this.productYear = productYear;
        this.productType = productType;
        this.supplyRate = supplyRate;
    }

    /** 수정(코드는 불변). */
    public void update(String name, ContentType contentType, boolean set, Integer price,
                       boolean taxFree, String grade, String catCode, String catName, boolean useYn,
                       String salesDivision, boolean ledgerVisible, boolean webVisible,
                       boolean stockManaged) {
        this.name = name;
        this.contentType = contentType;
        this.set = set;
        this.price = price;
        this.taxFree = taxFree;
        this.grade = grade;
        this.catCode = catCode;
        this.catName = catName;
        this.useYn = useYn;
        this.salesDivision = salesDivision;
        this.ledgerVisible = ledgerVisible;
        this.webVisible = webVisible;
        this.stockManaged = stockManaged;
    }

    /** 논리삭제(비활성화). */
    public void deactivate() {
        this.useYn = false;
    }

    /** BOM 완제품(세트)으로 표시. */
    public void markAsSet() {
        this.set = true;
    }
}
