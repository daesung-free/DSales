package com.daesung.sales.logistics.entity;

import com.daesung.sales.common.entity.BaseEntity;
import com.daesung.sales.partner.entity.Partner;
import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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

    /**
     * 작업 확인 시각 = 작업결과의 '확인' ○. 출력 다음 단계다(V73).
     *
     * <p>{@link #completedAt}과 <b>다른 축</b>이다 — 그쪽은 레거시에 쓰기 경로가 없어 늘 비어 있고,
     * 거기에 확인을 얹으면 나중에 레거시 데이터를 대조할 때 완료인지 확인인지 가릴 수 없다.
     */
    @Column(name = "acknowledged_at")
    private LocalDateTime acknowledgedAt;

    @Column(name = "acknowledged_by", length = 50)
    private String acknowledgedBy;

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

    /**
     * 발송구분(택배/화물). 근거: 정본 26p 클라이언트 정정 2026-07-25.
     * ⚠️기본값을 두지 않는다 — 정본이 "자동 기본값이 있는지"를 미해결로 남겼다.
     * 임의로 정하면 물류가 고르지 않은 건까지 한쪽으로 발송된 것처럼 보인다.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "delivery_type", length = 10)
    private DeliveryType deliveryType;

    /** 수령인 — 정본 26p "'택배' 선택 시 담당자 정보가 노출". */
    @Column(name = "receiver_name", length = 50)
    private String receiverName;

    @Column(name = "receiver_phone", length = 30)
    private String receiverPhone;

    /** 택배사(예: CJ대한통운). 수기·엑셀 일괄 입력. ⚠️택배사 API 연동은 하지 않는다. */
    @Column(name = "courier_name", length = 30)
    private String courierName;

    /** 송장번호. 수기·엑셀 일괄 입력. */
    @Column(name = "tracking_no", length = 50)
    private String trackingNo;

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

    /**
     * 출력 표시를 내린다 — 잘못 출력한 건을 되돌리는 경로(B-14).
     *
     * <p>‼️<b>사유는 호출부가 status_history 에 남긴다.</b> "언제 처음 지시가 나갔나"를 지우는
     * 행위라 누가·왜가 없으면 나중에 소명할 수 없다. 여기서는 값만 내린다.
     *
     * @return 실제로 내려갔으면 true(애초에 출력 전이면 false)
     */
    public boolean revertPrinted() {
        if (printedAt == null) {
            return false;
        }
        printedAt = null;
        return true;
    }

    /** 작업 확인 표시. 이미 확인된 건이면 최초 시각·처리자를 덮지 않는다(출력과 같은 규칙). */
    public boolean acknowledge(String by, LocalDateTime at) {
        if (acknowledgedAt != null) {
            return false;
        }
        acknowledgedAt = at;
        acknowledgedBy = by;
        return true;
    }

    /** 확인 표시를 내린다. 사유는 호출부가 status_history 에 남긴다. */
    public boolean revertAcknowledged() {
        if (acknowledgedAt == null) {
            return false;
        }
        acknowledgedAt = null;
        acknowledgedBy = null;
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

    /**
     * 발송구분·수령인 입력(26p). null인 항목은 건드리지 않는다 —
     * 발송구분만 고치려다 수령인이 지워지면 택배가 누구에게 가는지 알 수 없게 된다.
     */
    public void updateDelivery(DeliveryType deliveryType, String receiverName, String receiverPhone) {
        if (deliveryType != null) {
            this.deliveryType = deliveryType;
        }
        if (receiverName != null) {
            this.receiverName = receiverName;
        }
        if (receiverPhone != null) {
            this.receiverPhone = receiverPhone;
        }
    }

    /**
     * 송장 기록(수기·엑셀 일괄). null인 항목은 건드리지 않는다.
     *
     * <p>택배사 API를 부르지 않는다 — 연동 여부가 아직 미확정이라(물류팀 인터뷰 대기)
     * 지금은 사람이 받아 적은 값을 그대로 보관만 한다.
     */
    public void updateTracking(String courierName, String trackingNo) {
        if (courierName != null) {
            this.courierName = courierName;
        }
        if (trackingNo != null) {
            this.trackingNo = trackingNo;
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
