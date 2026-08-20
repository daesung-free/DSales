package com.daesung.sales.product.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
import com.daesung.sales.partner.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 거래처별 대분류 공급률·노출(34p). 근거: 정본 34p
 * "거래처별로 <b>상품군(대분류)마다</b> 공급률·노출여부를 사전 설정하여 주문/출고 및 신청사이트에 자동 반영",
 * 예시 "특약점 D모의고사 75%, 교재 60%"(회신 2026-08-20으로 'D모의고사'→대분류 '기타고사' 정정).
 *
 * <p>★예전엔 이 매핑이 <b>도서 × 거래처</b>였다(V19). 도서 단위로 두면 담당자가 도서 한 권마다
 * 거래처 전부를 깔아야 하고 신간마다 그 일을 반복해야 한다. 실제 운영은 상품군 단위 대표값이다.
 *
 * <p>매핑이 없는 거래처는 <b>도서 기본정보의 공급률</b>이 바탕값이 된다(정본 34p 명시).
 */
@Entity
@Table(name = "partner_supply_rate")   // 유니크 제약은 V42 소유(del_key 생성컬럼 포함)
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PartnerSupplyRate extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(name = "major_category", nullable = false, length = 20)
    private MajorCategory majorCategory;

    /** 공급률(%). 단가 = 도서 정가 × 공급률/100. */
    @Column(name = "supply_rate")
    private Integer supplyRate;

    /**
     * 할인액(원). 정본 34p 데이터 항목.
     *
     * <p>⚠️<b>값만 보관한다.</b> 레거시 공식 {@code if(할인액>0, 정가-할인액, 정가×공급률/100)}은
     * 단가 산출식이라, 금액에 반영하려면 매출의 금액 단일소스({@code Amounts})와
     * Sale이 할인액을 함께 들고 있어야 한다 — 별도 작업으로 둔다.
     */
    @Column(name = "discount_amount")
    private Integer discountAmount;

    /** Web게시여부 — 신청사이트(1p)에 이 거래처에게 노출할지. */
    @Column(name = "web_visible", nullable = false)
    private boolean webVisible = true;

    @Column(name = "use_yn", nullable = false)
    private boolean useYn = true;

    public static PartnerSupplyRate create(Partner partner, MajorCategory majorCategory,
                                           Integer supplyRate, Integer discountAmount,
                                           Boolean webVisible, Boolean useYn) {
        PartnerSupplyRate m = new PartnerSupplyRate();
        m.partner = partner;
        m.majorCategory = majorCategory;
        m.supplyRate = supplyRate;
        m.discountAmount = discountAmount;
        m.webVisible = (webVisible == null) || webVisible;
        m.useYn = (useYn == null) || useYn;
        return m;
    }

    /**
     * 수정. <b>null인 항목은 건드리지 않는다</b> — 공급률만 고치려다 Web게시가 꺼지면
     * 그 거래처가 신청사이트에서 조용히 사라진다(도서 마스터에서 겪은 것과 같은 사고).
     */
    public void update(Integer supplyRate, Integer discountAmount, Boolean webVisible, Boolean useYn) {
        if (supplyRate != null) {
            this.supplyRate = supplyRate;
        }
        if (discountAmount != null) {
            this.discountAmount = discountAmount;
        }
        if (webVisible != null) {
            this.webVisible = webVisible;
        }
        if (useYn != null) {
            this.useYn = useYn;
        }
    }
}
