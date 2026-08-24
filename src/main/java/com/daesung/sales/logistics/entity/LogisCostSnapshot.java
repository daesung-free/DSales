package com.daesung.sales.logistics.entity;

import com.daesung.sales.dsre.gateway.LogisCostDetailRow;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 마감월 물류작업비 확정분. 근거: 발주처 회신 2026-08-21 "과거 데이터 고정".
 *
 * <p>물류비는 저장된 값이 아니라 조회할 때마다 DSRE2에서 다시 계산한다. 그래서 단가를 고치면
 * 이미 청구가 끝난 달의 금액까지 바뀐다. 단가는 월별이 아니라 전역이라 잠글 수도 없다.
 * <b>마감하는 순간 결과를 굳히는 것</b>이 유일한 방법이다.
 *
 * <p>가장 잘게 저장한다(상품×학년×시행×신청×거래처). 구분·취소는 걸러서 저장하지 않고
 * <b>값으로 담는다</b> — 걸러 저장하면 그 조건으로만 볼 수 있게 된다.
 */
@Entity
@Table(name = "logis_cost_snapshot")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class LogisCostSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "period_year", nullable = false)
    private int periodYear;

    @Column(name = "period_month", nullable = false)
    private int periodMonth;

    @Column(name = "req_date")
    private LocalDate reqDate;

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

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(name = "apply_gn", length = 5)
    private String applyGn;

    @Column(nullable = false)
    private boolean canceled;

    /** 굳힌 시각 — 화면이 "이 숫자가 언제 기준인지" 알 수 있게 한다. */
    @Column(name = "computed_at", nullable = false)
    private LocalDateTime computedAt;

    @Column(name = "computed_by", length = 50)
    private String computedBy;

    public static LogisCostSnapshot of(int year, int month, LogisCostDetailRow r, String actor) {
        LogisCostSnapshot s = new LogisCostSnapshot();
        s.periodYear = year;
        s.periodMonth = month;
        s.reqDate = r.reqDate();
        s.reqCd = r.reqCd();
        s.productCode = r.productCode();
        s.productName = r.productName();
        s.grade = r.grade();
        s.dtlCd = r.dtlCd();
        s.detailName = r.detailName();
        s.partnerCode = r.partnerCode();
        s.partnerName = r.partnerName();
        s.materialQty = r.materialQty();
        s.paperQty = r.paperQty();
        s.paperAmount = r.paperAmount();
        s.omrQty = r.omrQty();
        s.omrAmount = r.omrAmount();
        s.etcQty = r.etcQty();
        s.etcAmount = r.etcAmount();
        s.inwon = r.inwon();
        s.basicAmount = r.basicAmount();
        s.tradeAmount = r.tradeAmount();
        s.totalAmount = r.totalAmount();
        s.applyGn = r.applyGn();
        s.canceled = r.canceled();
        s.computedAt = LocalDateTime.now();
        s.computedBy = actor;
        return s;
    }

    /** 저장분을 다시 명세 행으로. 라이브와 <b>같은 타입</b>이라 이후 처리(필터·접기)가 한 벌이다. */
    public LogisCostDetailRow toRow() {
        return new LogisCostDetailRow(reqDate, reqCd, productCode, productName, grade,
                dtlCd, detailName, partnerCode, partnerName,
                materialQty, paperQty, paperAmount, omrQty, omrAmount, etcQty, etcAmount,
                inwon, basicAmount, tradeAmount, totalAmount, applyGn, canceled);
    }
}
