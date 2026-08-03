package com.daesung.sales.school.entity;

import com.daesung.sales.common.entity.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 학교/학원 마스터(35p). 근거: 요구사항 35p + 레거시 DSLab.schData + DSRE tbl_cust_ref/school_info/hakwon_info.
 *
 * <p><b>식별키 = (거래처코드, 학교코드) 복합</b>. DSRE2 {@code tbl_cust_ref}의 UNIQUE (CUST_CD, MGR_GN, MGR_CD)
 * 및 레거시 {@code schData}(schCode·custCode 별개 컬럼)와 동일 체계다.
 * (V22의 "학교코드=거래처코드 동일값" 전제는 원본과 달라 V26에서 폐기했다.)
 *
 * <p><b>필드는 두 종류로 나뉜다 — 동기화가 이 경계를 넘으면 안 된다.</b>
 * <ul>
 *   <li>DSRE 관리: 거래처명·도시·지역·학교명·학교Y/N → 동기화 시 덮어씀
 *   <li>매출프로그램 전용: 거래처구분·학교/학원구분·메모 → 동기화가 <b>건드리지 않음</b>(수기 입력값 보존)
 * </ul>
 */
@Entity
@Table(name = "schools")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학교코드(DSRE MGR_CD). 거래처코드와 함께 동기화 매칭키. */
    @Column(name = "school_code", nullable = false, length = 20)
    private String schoolCode;

    /** 거래처코드(partner.code / DSRE CUST_CD). 매칭키 일부라 NULL 불가(미지정은 빈문자). */
    @Column(name = "cust_code", nullable = false, length = 20)
    private String custCode;

    /** 거래처명(표시용). */
    @Column(name = "cust_name", length = 100)
    private String custName;

    /** 도시. */
    @Column(length = 50)
    private String city;

    /** 지역(관할). */
    @Column(length = 50)
    private String region;

    /** 학교/학원명. */
    @Column(name = "school_name", length = 100)
    private String schoolName;

    /** 학교 Y/N. */
    @Column(name = "is_school", nullable = false)
    private boolean isSchool = true;

    /** 학교/학원구분(직접입력). */
    @Enumerated(EnumType.STRING)
    @Column(name = "school_type", nullable = false, length = 10)
    private SchoolType schoolType = SchoolType.SCHOOL;

    /** 거래처구분(특약점/기타학원/B2B 등, 직접입력). */
    @Column(name = "client_category", length = 30)
    private String clientCategory;

    @Column(length = 500)
    private String memo;

    /** 출처. MANUAL(매출프로그램 전용)은 DSRE 동기화가 건드리지 않는다. */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private SchoolSource source = SchoolSource.MANUAL;

    /** 사용여부. DSRE2에서 사라진 행은 삭제하지 않고 false(미사용)로 둔다 — 과거 매출 이력 보존. */
    @Column(nullable = false)
    private boolean active = true;

    public static School create(String schoolCode, String custCode, String custName, String city, String region,
                                String schoolName, boolean isSchool, SchoolType schoolType,
                                String clientCategory, String memo) {
        School s = new School();
        s.schoolCode = schoolCode;
        s.custCode = (custCode == null) ? "" : custCode;
        s.custName = custName;
        s.city = city;
        s.region = region;
        s.schoolName = schoolName;
        s.isSchool = isSchool;
        s.schoolType = (schoolType == null) ? SchoolType.SCHOOL : schoolType;
        s.clientCategory = clientCategory;
        s.memo = memo;
        return s;
    }

    /** 수정(학교코드 불변). */
    public void update(String custCode, String custName, String city, String region, String schoolName,
                       boolean isSchool, SchoolType schoolType, String clientCategory, String memo) {
        this.custCode = (custCode == null) ? "" : custCode;
        this.custName = custName;
        this.city = city;
        this.region = region;
        this.schoolName = schoolName;
        this.isSchool = isSchool;
        this.schoolType = (schoolType == null) ? SchoolType.SCHOOL : schoolType;
        this.clientCategory = clientCategory;
        this.memo = memo;
    }

    /** DSRE에서 온 행으로 표시(신규 동기화 생성 시). */
    public School fromDsre() {
        this.source = SchoolSource.DSRE;
        return this;
    }

    /**
     * 동기화 덮어쓰기 — <b>DSRE 관리 필드만</b>.
     * 거래처구분·학교/학원구분·메모는 매출프로그램 수기 입력값이라 손대지 않는다(발주처 확정 2026-08).
     * 동기화로 다시 나타난 행은 미사용에서 되살린다.
     */
    public void applyDsreFields(String custName, String city, String region, String schoolName, boolean isSchool) {
        this.custName = custName;
        this.city = city;
        this.region = region;
        this.schoolName = schoolName;
        this.isSchool = isSchool;
        this.active = true;
    }

    /** DSRE2에서 사라진 행 처리 — 삭제가 아니라 미사용. */
    public void markUnused() {
        this.active = false;
    }
}
