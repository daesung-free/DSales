package com.daesung.sales.school.dto;

import com.daesung.sales.school.entity.School;
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
        String memo
) {
    public static SchoolResponse from(School s) {
        return new SchoolResponse(s.getId(), s.getSchoolCode(), s.getCustCode(), s.getCustName(),
                s.getCity(), s.getRegion(), s.getSchoolName(), s.isSchool(), s.getSchoolType(),
                s.getClientCategory(), s.getMemo());
    }
}
