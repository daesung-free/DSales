package com.daesung.sales.receivable.entity;

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
import java.time.LocalDate;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 수금(amtData 정규화). 채권 잔액에서 차감. 어음 필드는 입금구분=어음일 때만.
 *
 * <p>구분이 <b>두 축</b>이다(정본 23p) — {@code collKind}(수금구분, 무슨 명목)와
 * {@code collType}(입금구분, 어떤 형태). 하나로 합치면 "어음으로 받은 도서대금"을 표현할 수 없다.
 */
@Entity
@Table(name = "collection")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Collection extends SoftDeletableEntity {

    /** 레거시 화면이 고정으로 넣던 값. 미입력 시 이 값으로 채운다. */
    public static final String DEFAULT_KIND = "도서대금";

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

    /**
     * 수금구분 — <b>무슨 명목으로 받은 돈인가</b>. 근거: 레거시 collKind.
     *
     * <p>레거시 화면은 {@code "도서대금"}으로 고정하고 편집을 막아 두었다(수금등록.vb:19~20).
     * 우리도 기본값은 같지만 컬럼은 열어 둔다 — 정본 23p가 데이터 항목으로 명시한 이상
     * 다른 명목으로 받을 여지를 막을 이유가 없다.
     */
    @Column(name = "coll_kind", nullable = false, length = 30)
    private String collKind = DEFAULT_KIND;

    /** 입금구분 — <b>어떤 형태로 들어왔는가</b>(현금/어음/선수금/대체). 근거: 레거시 collType. */
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
                                    Partner partner, String collKind,
                                    CollectionType collType, long collAmt, String promissoryNo,
                                    LocalDate promissoryDue, String bankName, String branchName, String memo) {
        Collection c = new Collection();
        c.collectionNo = collectionNo;
        c.collDate = collDate;
        c.writeDate = writeDate;
        c.partner = partner;
        c.collKind = (collKind == null || collKind.isBlank()) ? DEFAULT_KIND : collKind.trim();
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

    /**
     * 수정(23p "CRUD 전체"). 수금번호·거래처는 바꾸지 않는다 —
     * 거래처를 옮기면 두 거래처의 채권 잔액이 동시에 틀어지고, 그건 수정이 아니라 취소 후 재등록이다.
     *
     * <p>입금구분을 어음이 아닌 값으로 바꾸면 어음 정보를 <b>지운다</b>. 남겨두면
     * "현금인데 어음번호가 붙은" 행이 생겨, 어음 관리 화면에 유령 어음이 뜬다.
     */
    public void update(LocalDate collDate, LocalDate writeDate, String collKind,
                       CollectionType collType, long collAmt, String promissoryNo,
                       LocalDate promissoryDue, String bankName, String branchName, String memo) {
        this.collDate = collDate;
        this.writeDate = writeDate;
        this.collKind = (collKind == null || collKind.isBlank()) ? DEFAULT_KIND : collKind.trim();
        this.collType = collType;
        this.collAmt = collAmt;
        if (collType == CollectionType.PROMISSORY) {
            this.promissoryNo = promissoryNo;
            this.promissoryDue = promissoryDue;
            this.bankName = bankName;
            this.branchName = branchName;
        } else {
            this.promissoryNo = null;
            this.promissoryDue = null;
            this.bankName = null;
            this.branchName = null;
        }
        this.memo = memo;
    }
}
