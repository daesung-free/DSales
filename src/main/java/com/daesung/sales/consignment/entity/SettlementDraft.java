package com.daesung.sales.consignment.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 위탁정산 <b>임시저장(초안)</b>. 근거: 정본 13p "매출등록은 [임시저장] → [매출확정등록] 2단계".
 *
 * <p>★<b>아무것도 확정하지 않는다.</b> 미결 잔여도, 재고도, 매출도 그대로다.
 * 정본 13p 원문이 "임시저장 상태의 데이터는 <b>매출 미반영</b>임을 명확히 구분해야 한다"이다.
 * 그래서 이건 업무 원장이 아니라 <b>작성 중인 입력값</b>이다.
 *
 * <p>★금액까지 저장하는 이유 — 초안은 "그때 본 화면"을 재현해야 한다.
 * 수량만 남기고 확정 때 다시 계산하면, 그 사이 거래처 단가가 바뀌었을 때
 * 담당자가 보고 저장한 금액과 실제 확정 금액이 달라진다.
 */
@Entity
@Table(name = "settlement_draft")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class SettlementDraft extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 초안번호(DRAFT-yyyyMMdd-n). 화면이 draftId로 쓴다. */
    @Column(name = "draft_no", nullable = false, length = 30)
    private String draftNo;

    /** 매출 인식일 — 확정하면 이 날짜로 매출이 선다. */
    @Column(name = "sales_date", nullable = false)
    private LocalDate salesDate;

    @Column(name = "total_qty", nullable = false)
    private int totalQty;

    @Column(name = "total_amount", nullable = false)
    private long totalAmount;

    @Column(length = 200)
    private String memo;

    @OneToMany(mappedBy = "draft", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<SettlementDraftLine> lines = new ArrayList<>();

    public static SettlementDraft create(String draftNo, LocalDate salesDate, String memo) {
        SettlementDraft d = new SettlementDraft();
        d.draftNo = draftNo;
        d.salesDate = salesDate;
        d.memo = memo;
        return d;
    }

    /** 행 추가 + 합계 누적. 합계를 따로 계산하지 않도록 여기서만 더한다. */
    public void addLine(SettlementDraftLine line) {
        line.attachTo(this);
        lines.add(line);
        totalQty += line.getSettleQty();
        totalAmount += line.getTotalAmount();
    }
}
