package com.daesung.sales.receivable.entity;

import com.daesung.sales.common.entity.BaseEntity;
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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** 수금(amtData 정규화). 채권 잔액에서 차감. 어음 필드는 collType=PROMISSORY일 때만. */
@Entity
@Table(name = "collection")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collection extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "collection_no", unique = true, length = 30)
    private String collectionNo;

    @Column(name = "coll_date", nullable = false)
    private LocalDate collDate;

    /**
     * 기장일자(회계 기표일). 근거: 레거시 AmtData.writeDate — 수금등록 그리드에서 수금일자와
     * 나란히 보여 준다. 돈이 들어온 날과 장부에 올린 날이 다를 수 있어 재무팀이 둘을 나눠 본다.
     * 수금일자로 자동 채우지 않는다(같다고 단정하면 따로 둘 이유가 없다).
     */
    @Column(name = "write_date")
    private LocalDate writeDate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "partner_id", nullable = false)
    private Partner partner;

    @Enumerated(EnumType.STRING)
    @Column(name = "coll_type", nullable = false, length = 20)
    private CollectionType collType;

    @Column(name = "coll_amt", nullable = false)
    private long collAmt;

    @Column(name = "promissory_no", length = 50)
    private String promissoryNo;

    @Column(name = "promissory_due")
    private LocalDate promissoryDue;

    @Column(name = "bank_name", length = 100)
    private String bankName;

    @Column(name = "branch_name", length = 100)
    private String branchName;

    @Column(length = 1000)
    private String memo;

    public static Collection create(String collectionNo, LocalDate collDate, LocalDate writeDate,
                                    Partner partner,
                                    CollectionType collType, long collAmt, String promissoryNo,
                                    LocalDate promissoryDue, String bankName, String branchName, String memo) {
        Collection c = new Collection();
        c.collectionNo = collectionNo;
        c.collDate = collDate;
        c.writeDate = writeDate;
        c.partner = partner;
        c.collType = collType;
        c.collAmt = collAmt;
        // 어음일 때만 어음 정보 보존(레거시 IIf 처리).
        if (collType == CollectionType.PROMISSORY) {
            c.promissoryNo = promissoryNo;
            c.promissoryDue = promissoryDue;
            c.bankName = bankName;
            c.branchName = branchName;
        }
        c.memo = memo;
        return c;
    }
}
