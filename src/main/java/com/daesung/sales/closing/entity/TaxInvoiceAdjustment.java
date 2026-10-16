package com.daesung.sales.closing.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.partner.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 계산서신고 <b>수기 보정</b>(발주처 1차회신 2026-10-02 #24).
 *
 * <p>★<b>계산서 내역 자체는 저장하지 않는다.</b> 지금처럼 매출에서 파생해 만들고,
 * 담당자가 손댄 값만 이 행에 얹는다. 내역을 통째로 저장하면 나중에 매출을 고쳤을 때
 * 계산서가 따라가지 않아 <b>장부와 신고가 조용히 갈린다</b>.
 *
 * <p>★키는 <b>작성일자 · 거래처 · 과세구분</b> — 계산서 한 장의 단위다.
 * 월 단위로 조회하면 작성일자가 그 달 말일이라 자연히 "월×거래처×과세구분"이 된다
 * (발주처 설명: "해당월의 매출이 모두 해당월 말일자로 조회되는 구조").
 *
 * <p>‼️<b>품목은 보정 대상이 아니다.</b> 품목·수량이 틀렸다면 고칠 곳은 매출이다
 * (매출 수정 API). 여기서 고치면 계산서만 맞고 장부는 그대로 틀린 채 남는다.
 */
@Entity
@Table(name = "tax_invoice_adjustment")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TaxInvoiceAdjustment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "issue_date", nullable = false)
    private LocalDate issueDate;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "tax_bucket", nullable = false, length = 10)
    private String taxBucket;

    @Column(name = "partner_name", length = 100)
    private String partnerName;

    @Column(name = "biz_no", length = 20)
    private String bizNo;

    @Column(name = "boss_name", length = 50)
    private String bossName;

    @Column(length = 255)
    private String address;

    @Column(name = "biz_status", length = 50)
    private String bizStatus;

    @Column(name = "biz_item", length = 50)
    private String bizItem;

    @Column(length = 100)
    private String email1;

    @Column(length = 100)
    private String email2;

    /** 공급가액 합계 보정. null 이면 파생값 그대로. */
    @Column(name = "supply_total")
    private Long supplyTotal;

    /** 세액 합계 보정. null 이면 파생값 그대로. */
    @Column(name = "tax_total")
    private Long taxTotal;

    @Column(name = "receipt_type", length = 2)
    private String receiptType;

    /**
     * 작성일자 보정(현장 피드백 2026-10-14 B-6).
     *
     * <p>★{@link #issueDate} 는 <b>어느 계산서인지 가리키는 키</b>라 바꾸지 않는다 —
     * 그걸 고치면 보정을 다시 찾지 못한다. 표시할 날짜만 여기 둔다.
     */
    @Column(name = "new_issue_date")
    private LocalDate newIssueDate;

    @Column(length = 500)
    private String memo;

    @Column(length = 500)
    private String reason;

    public static TaxInvoiceAdjustment of(LocalDate issueDate, Partner partner, String taxBucket) {
        TaxInvoiceAdjustment a = new TaxInvoiceAdjustment();
        a.issueDate = issueDate;
        a.partner = partner;
        a.taxBucket = taxBucket;
        return a;
    }

    /**
     * 보정값 통째 교체(upsert).
     *
     * <p>★<b>부분 갱신이 아니라 교체다.</b> 화면이 폼 전체를 들고 있고, 비운 칸은
     * "보정을 지웠다"는 뜻이다 — null 을 무시하면 한번 넣은 보정을 되돌릴 수 없게 된다.
     * (거래처 수정이 부분 갱신이라 값을 날린 적이 있어, 여기는 의미를 반대로 못박는다.)
     */
    public void replace(String partnerName, String bizNo, String bossName, String address,
                        String bizStatus, String bizItem, String email1, String email2,
                        Long supplyTotal, Long taxTotal, String receiptType,
                        LocalDate newIssueDate, String memo, String reason) {
        this.newIssueDate = newIssueDate;
        this.partnerName = partnerName;
        this.bizNo = bizNo;
        this.bossName = bossName;
        this.address = address;
        this.bizStatus = bizStatus;
        this.bizItem = bizItem;
        this.email1 = email1;
        this.email2 = email2;
        this.supplyTotal = supplyTotal;
        this.taxTotal = taxTotal;
        this.receiptType = receiptType;
        this.memo = memo;
        this.reason = reason;
    }
}
