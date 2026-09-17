package com.daesung.sales.logistics.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
import com.daesung.sales.dsre.gateway.LogisCostDetailRow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 수기 등록 물류작업비(28p 에디팅 모드). 근거: 발주처 회신 2026-08-21.
 *
 * <p>★자동계산분을 직접 고치지 않는 이유: 그것은 우리가 저장한 값이 아니라 조회할 때마다
 * DSRE2에서 다시 만드는 값이다. 손을 대도 다음 조회에서 새로 계산되며 수정이 날아간다.
 * 발주처가 "기존 자동계산 로직에는 영향이 없어야" 한다고 못박은 것과도 맞는다.
 *
 * <p>조회 시 자동계산분과 <b>같은 타입</b>({@link LogisCostDetailRow})으로 합쳐지므로
 * 소계·누계·합계·총계에 그대로 잡힌다 — 합산 코드를 따로 두지 않는다.
 */
@Entity
@Table(name = "logis_cost_manual")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogisCostManual extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 접수일자 — 자동계산분과 같은 귀속 축이라야 한 화면에서 합쳐진다. */
    @Column(name = "req_date", nullable = false)
    private LocalDate reqDate;

    /** 신청번호. 수기 건은 대응 신청이 없을 수 있어 0을 허용한다. */
    @Column(name = "req_cd", nullable = false)
    private int reqCd;

    @Column(name = "product_code", length = 20)
    private String productCode;

    @Column(name = "product_name", length = 100)
    private String productName;

    @Column(length = 10)
    private String grade;

    @Column(name = "dtl_cd", nullable = false)
    private int dtlCd;

    @Column(name = "detail_name", length = 100)
    private String detailName;

    @Column(name = "partner_code", length = 10)
    private String partnerCode;

    @Column(name = "partner_name", length = 100)
    private String partnerName;

    @Column(name = "material_qty", nullable = false)
    private long materialQty;

    @Column(name = "paper_qty", nullable = false)
    private long paperQty;

    @Column(name = "paper_amount", nullable = false)
    private long paperAmount;

    @Column(name = "omr_qty", nullable = false)
    private long omrQty;

    @Column(name = "omr_amount", nullable = false)
    private long omrAmount;

    @Column(name = "etc_qty", nullable = false)
    private long etcQty;

    @Column(name = "etc_amount", nullable = false)
    private long etcAmount;

    @Column(nullable = false)
    private int inwon;

    @Column(name = "basic_amount", nullable = false)
    private long basicAmount;

    @Column(name = "trade_amount", nullable = false)
    private long tradeAmount;

    /** 구분(S=일반/A=사고). 자동계산분과 같은 축으로 걸러지게 한다. */
    @Column(name = "apply_gn", length = 5)
    private String applyGn;

    /** 왜 수기로 넣었는지 — 나중에 이 금액의 근거를 물을 때 답이 된다. */
    @Column(length = 500)
    private String memo;

    public static LogisCostManual create(LocalDate reqDate, Integer reqCd, String productCode,
                                         String productName, String grade, Integer dtlCd,
                                         String detailName, String partnerCode, String partnerName,
                                         long materialQty, long paperQty, long paperAmount,
                                         long omrQty, long omrAmount, long etcQty, long etcAmount,
                                         int inwon, long basicAmount, long tradeAmount,
                                         String applyGn, String memo) {
        LogisCostManual m = new LogisCostManual();
        m.reqDate = reqDate;
        m.reqCd = (reqCd == null) ? 0 : reqCd;
        m.dtlCd = (dtlCd == null) ? 0 : dtlCd;
        m.apply(productCode, productName, grade, detailName, partnerCode, partnerName,
                materialQty, paperQty, paperAmount, omrQty, omrAmount, etcQty, etcAmount,
                inwon, basicAmount, tradeAmount, applyGn, memo);
        return m;
    }

    /** 수정. 접수일자·신청번호·시행코드는 귀속 축이라 바꾸지 않는다(옮기려면 지우고 다시 넣는다). */
    public void apply(String productCode, String productName, String grade, String detailName,
                      String partnerCode, String partnerName,
                      long materialQty, long paperQty, long paperAmount,
                      long omrQty, long omrAmount, long etcQty, long etcAmount,
                      int inwon, long basicAmount, long tradeAmount, String applyGn, String memo) {
        this.productCode = productCode;
        this.productName = productName;
        this.grade = grade;
        this.detailName = detailName;
        this.partnerCode = partnerCode;
        this.partnerName = partnerName;
        this.materialQty = materialQty;
        this.paperQty = paperQty;
        this.paperAmount = paperAmount;
        this.omrQty = omrQty;
        this.omrAmount = omrAmount;
        this.etcQty = etcQty;
        this.etcAmount = etcAmount;
        this.inwon = inwon;
        this.basicAmount = basicAmount;
        this.tradeAmount = tradeAmount;
        this.applyGn = applyGn;
        this.memo = memo;
    }

    /** 복사본(28p '복사'). 같은 날짜·같은 내용으로 새 행을 만든다 — 비슷한 건을 반복 입력하는 화면이다. */
    public LogisCostManual copy() {
        return create(reqDate, reqCd, productCode, productName, grade, dtlCd, detailName,
                partnerCode, partnerName, materialQty, paperQty, paperAmount,
                omrQty, omrAmount, etcQty, etcAmount, inwon, basicAmount, tradeAmount,
                applyGn, memo);
    }

    /** 자동계산분과 같은 타입으로 — 이후 필터·접기·소계가 한 벌로 돈다. */
    public LogisCostDetailRow toRow() {
        long total = paperAmount + omrAmount + etcAmount + basicAmount + tradeAmount;
        return new LogisCostDetailRow(reqDate, reqCd, productCode, productName, grade,
                dtlCd, detailName, partnerCode, partnerName,
                // 수기 등록분엔 지역·도시명·학교명이 없다 — 담당자가 금액만 적는 화면이다.
                null, null, null,
                materialQty, paperQty, paperAmount, omrQty, omrAmount, etcQty, etcAmount,
                inwon, basicAmount, tradeAmount, total, applyGn, false, true);
    }
}
