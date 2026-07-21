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

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    public static Product create(String code, String name, ContentType contentType, boolean set,
                                 Integer price, boolean taxFree, String grade, boolean useYn) {
        Product p = new Product();
        p.code = code;
        p.name = name;
        p.contentType = contentType;
        p.set = set;
        p.price = price;
        p.taxFree = taxFree;
        p.grade = grade;
        p.useYn = useYn;
        return p;
    }

    /** 수정(코드는 불변). */
    public void update(String name, ContentType contentType, boolean set, Integer price,
                       boolean taxFree, String grade, boolean useYn) {
        this.name = name;
        this.contentType = contentType;
        this.set = set;
        this.price = price;
        this.taxFree = taxFree;
        this.grade = grade;
        this.useYn = useYn;
    }

    /** 논리삭제(비활성화). */
    public void deactivate() {
        this.useYn = false;
    }
}
