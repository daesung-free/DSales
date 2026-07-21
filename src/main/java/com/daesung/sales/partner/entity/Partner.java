package com.daesung.sales.partner.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 거래처. 담보/여신은 채권 도메인용(custData.assureAmt/Exp/Note). 근거: custData. */
@Entity
@Table(name = "partners")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Partner extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 20)
    private String code;

    @Column(nullable = false, length = 100)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PartnerType type = PartnerType.NORMAL;

    /** 담보금액(여신한도). 채권 담보비율=잔액/assureAmount. */
    @Column(name = "assure_amount")
    private Long assureAmount;

    /** 담보 만기일. */
    @Column(name = "assure_expiry")
    private LocalDate assureExpiry;

    /** 담보 내용(비고). */
    @Column(name = "assure_note", length = 500)
    private String assureNote;

    public static Partner create(String code, String name, PartnerType type) {
        Partner p = new Partner();
        p.code = code;
        p.name = name;
        p.type = (type == null) ? PartnerType.NORMAL : type;
        return p;
    }

    /** 수정(코드는 불변). */
    public void update(String name, PartnerType type) {
        this.name = name;
        this.type = (type == null) ? PartnerType.NORMAL : type;
    }

    /** 담보(여신) 정보 설정. */
    public void updateCredit(Long assureAmount, LocalDate assureExpiry, String assureNote) {
        this.assureAmount = assureAmount;
        this.assureExpiry = assureExpiry;
        this.assureNote = assureNote;
    }
}
