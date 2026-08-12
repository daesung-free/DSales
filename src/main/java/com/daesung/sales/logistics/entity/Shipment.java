package com.daesung.sales.logistics.entity;

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
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 발송(작업) 단위. 레거시 {@code sendData} 대응.
 *
 * <p>매출이 "얼마 팔렸나"라면 이쪽은 <b>"그걸 언제 뽑아서 몇 박스로 언제 보냈나"</b>다.
 * 작업요청서가 값을 채우고 작업결과가 읽는다.
 */
@Entity
@Table(name = "shipment")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Shipment extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 분류(레거시 tradeClass). 매출구분에서 따온다. */
    @Column(name = "trade_class", length = 20)
    private String tradeClass;

    @Column(name = "trade_date", nullable = false)
    private LocalDate tradeDate;

    @Column(name = "trade_seq", nullable = false)
    private int tradeSeq = 1;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Column(name = "school_code", length = 30)
    private String schoolCode;

    @Column(name = "school_name", length = 100)
    private String schoolName;

    /** 작업요청서 출력 시각 = 작업결과의 '출력' ○. */
    @Column(name = "printed_at")
    private LocalDateTime printedAt;

    /** ⚠️레거시에 쓰기 경로가 없다(읽기만). 우리도 임의로 만들지 않는다. */
    @Column(name = "completed_at")
    private LocalDateTime completedAt;

    @Column(name = "box_count", nullable = false)
    private int boxCount;

    @Column(name = "sent_date")
    private LocalDate sentDate;

    @Column(name = "send_memo", length = 1000)
    private String sendMemo;

    @Column(length = 500)
    private String memo;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    public static Shipment of(String tradeClass, LocalDate tradeDate, Partner partner,
                              String schoolCode, String schoolName, String memo) {
        Shipment s = new Shipment();
        s.tradeClass = tradeClass;
        s.tradeDate = tradeDate;
        s.partner = partner;
        s.schoolCode = schoolCode;
        s.schoolName = schoolName;
        s.memo = memo;
        return s;
    }

    /** 작업요청서 출력 처리. 재출력해도 최초 시각을 덮지 않는다 — '언제 처음 지시가 나갔나'가 기록의 의미다. */
    public boolean markPrinted(LocalDateTime at) {
        if (printedAt != null) {
            return false;
        }
        printedAt = at;
        return true;
    }

    /** 물류 발송정보 입력(작업요청서.vb:915 UPDATE와 동일 항목). null인 항목은 건드리지 않는다. */
    public void updateShipping(Integer boxCount, LocalDate sentDate, String sendMemo) {
        if (boxCount != null) {
            this.boxCount = boxCount;
        }
        if (sentDate != null) {
            this.sentDate = sentDate;
        }
        if (sendMemo != null) {
            this.sendMemo = sendMemo;
        }
    }

    public void markDeleted(String by) {
        this.deletedAt = LocalDateTime.now();
    }

    /** 발송 완료 여부 — 레거시 작업결과가 발송일 유무로 판단한다. */
    public boolean isSent() {
        return sentDate != null;
    }
}
