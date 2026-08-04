package com.daesung.sales.person.entity;

import com.daesung.sales.common.entity.SoftDeletableEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.SQLRestriction;

/**
 * 개인회원(구 IC). 근거: 레거시 DSLab.personData + 개인회원관리.vb.
 * 개인이 직접 결제한 건의 수취인·배송지를 관리한다(특약점 경유가 아닌 개인 구매).
 *
 * <p>레거시는 물리 DELETE 했으나(개인회원관리.vb:500) 게이트규칙에 따라 논리삭제로 전환.
 */
@Entity
@Table(name = "person_member")
@SQLRestriction("deleted_at is null")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PersonMember extends SoftDeletableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "fiscal_year", length = 4)
    private String fiscalYear;

    @Column(name = "input_date")
    private LocalDateTime inputDate;

    /** 결제일 — 레거시 조회의 기본 기간축(payDate between). */
    @Column(name = "pay_date")
    private LocalDateTime payDate;

    @Column(name = "student_id", nullable = false, length = 20)
    private String studentId;

    @Column(name = "student_name", nullable = false, length = 20)
    private String studentName;

    @Column(name = "goods_code", length = 50)
    private String goodsCode;

    @Column(name = "goods_name", length = 50)
    private String goodsName;

    @Column(length = 6)
    private String post;

    @Column(length = 100)
    private String addr1;

    @Column(length = 100)
    private String addr2;

    /** 수취인명(personData.name). */
    @Column(length = 20)
    private String receiver;

    @Column(length = 20)
    private String tel1;

    @Column(length = 20)
    private String tel2;

    @Column(length = 500)
    private String memo;

    @Column(length = 30)
    private String manager;

    public static PersonMember create(String fiscalYear, LocalDateTime inputDate, LocalDateTime payDate,
                                      String studentId, String studentName, String goodsCode, String goodsName,
                                      String post, String addr1, String addr2, String receiver,
                                      String tel1, String tel2, String memo, String manager) {
        PersonMember m = new PersonMember();
        m.fiscalYear = fiscalYear;
        m.inputDate = (inputDate == null) ? LocalDateTime.now() : inputDate;
        m.payDate = payDate;
        m.studentId = studentId;
        m.studentName = studentName;
        m.goodsCode = goodsCode;
        m.goodsName = goodsName;
        m.post = post;
        m.addr1 = addr1;
        m.addr2 = addr2;
        m.receiver = receiver;
        m.tel1 = tel1;
        m.tel2 = tel2;
        m.memo = memo;
        m.manager = manager;
        return m;
    }

    public void update(String fiscalYear, LocalDateTime payDate, String studentId, String studentName,
                       String goodsCode, String goodsName, String post, String addr1, String addr2,
                       String receiver, String tel1, String tel2, String memo, String manager) {
        this.fiscalYear = fiscalYear;
        this.payDate = payDate;
        this.studentId = studentId;
        this.studentName = studentName;
        this.goodsCode = goodsCode;
        this.goodsName = goodsName;
        this.post = post;
        this.addr1 = addr1;
        this.addr2 = addr2;
        this.receiver = receiver;
        this.tel1 = tel1;
        this.tel2 = tel2;
        this.memo = memo;
        this.manager = manager;
    }
}
