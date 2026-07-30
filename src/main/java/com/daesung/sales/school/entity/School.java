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
 * 학교/학원 마스터(35p). 근거: 요구사항 35p + 레거시 DSLab.schData + DSRE tbl_school_info/hakwon_info.
 * 학교코드=거래처코드 강제 동기화(동일값). 거래처구분·학교/학원구분은 매출프로그램 직접입력.
 */
@Entity
@Table(name = "schools")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class School extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 학교코드(=거래처코드 동일값, DSRE MGR_CD). */
    @Column(name = "school_code", nullable = false, unique = true, length = 20)
    private String schoolCode;

    /** 거래처코드(partner.code 매핑). */
    @Column(name = "cust_code", length = 20)
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

    public static School create(String schoolCode, String custCode, String custName, String city, String region,
                                String schoolName, boolean isSchool, SchoolType schoolType,
                                String clientCategory, String memo) {
        School s = new School();
        s.schoolCode = schoolCode;
        s.custCode = custCode;
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
        this.custCode = custCode;
        this.custName = custName;
        this.city = city;
        this.region = region;
        this.schoolName = schoolName;
        this.isSchool = isSchool;
        this.schoolType = (schoolType == null) ? SchoolType.SCHOOL : schoolType;
        this.clientCategory = clientCategory;
        this.memo = memo;
    }
}
