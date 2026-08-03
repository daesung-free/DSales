package com.daesung.sales.school.dto;

import com.daesung.sales.school.entity.School;
import com.daesung.sales.school.entity.SchoolSource;
import com.daesung.sales.school.entity.SchoolType;

/** 학교 응답(35p 컬럼). */
public record SchoolResponse(
        Long id,
        String schoolCode,
        String custCode,
        String custName,
        String city,
        String region,
        String schoolName,
        boolean isSchool,
        SchoolType schoolType,
        String clientCategory,
        String memo,
        /** 출처(DSRE=동기화 유입 / MANUAL=매출프로그램 전용, 동기화 미대상). */
        SchoolSource source,
        /** 사용여부. DSRE2에서 사라지면 false(삭제 아님). */
        boolean active
) {
    public static SchoolResponse from(School s) {
        return new SchoolResponse(s.getId(), s.getSchoolCode(), s.getCustCode(), s.getCustName(),
                s.getCity(), s.getRegion(), s.getSchoolName(), s.isSchool(), s.getSchoolType(),
                s.getClientCategory(), s.getMemo(), s.getSource(), s.isActive());
    }
}
