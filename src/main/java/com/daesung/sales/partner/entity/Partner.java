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

    /** 거래처명2 = 합쳐진 풀네임(예: '진주 이룸도서'). 기존 name 유지. DSRE CUST_FNM. */
    @Column(nullable = false, length = 100)
    private String name;

    /** 도시명(예: '진주'). DSRE CITY_NM. */
    @Column(name = "city_name", length = 50)
    private String cityName;

    /** 거래처명1 = 상호만(예: '이룸도서'). DSRE CUST_NM. */
    @Column(name = "name1", length = 100)
    private String name1;

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

    // ── 세무(계산서 공급받는자) 정보 ──
    @Column(name = "biz_no", length = 20)
    private String bizNo;          // 사업자번호

    @Column(name = "boss_name", length = 50)
    private String bossName;       // 대표자

    @Column(length = 200)
    private String addr1;

    @Column(length = 200)
    private String addr2;

    @Column(name = "biz_status", length = 100)
    private String bizStatus;      // 업태

    @Column(name = "biz_item", length = 100)
    private String bizItem;        // 종목

    @Column(length = 100)
    private String email1;

    @Column(length = 100)
    private String email2;

    public static Partner create(String code, String name, PartnerType type) {
        Partner p = new Partner();
        p.code = code;
        p.name = name;
        p.type = (type == null) ? PartnerType.NORMAL : type;
        return p;
    }

    /** 수정(코드는 불변). name=거래처명2(풀네임), cityName=도시명, name1=상호만. */
    public void update(String name, String cityName, String name1, PartnerType type) {
        this.name = name;
        this.cityName = cityName;
        this.name1 = name1;
        this.type = (type == null) ? PartnerType.NORMAL : type;
    }

    /** 거래처명 분리 필드(도시명·거래처명1) 설정 — 등록 시 사용. */
    public void applyNames(String cityName, String name1) {
        this.cityName = cityName;
        this.name1 = name1;
    }

    /** 담보(여신) 정보 설정. */
    public void updateCredit(Long assureAmount, LocalDate assureExpiry, String assureNote) {
        this.assureAmount = assureAmount;
        this.assureExpiry = assureExpiry;
        this.assureNote = assureNote;
    }

    /** 세무(계산서 공급받는자) 정보 설정. */
    public void updateTaxInfo(String bizNo, String bossName, String addr1, String addr2,
                              String bizStatus, String bizItem, String email1, String email2) {
        this.bizNo = bizNo;
        this.bossName = bossName;
        this.addr1 = addr1;
        this.addr2 = addr2;
        this.bizStatus = bizStatus;
        this.bizItem = bizItem;
        this.email1 = email1;
        this.email2 = email2;
    }
}
