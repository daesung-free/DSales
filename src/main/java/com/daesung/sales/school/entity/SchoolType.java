package com.daesung.sales.school.entity;

/** 학교/학원구분(35p, 매출프로그램 직접입력). DSRE tbl_school_info↔tbl_hakwon_info 대응. */
public enum SchoolType {
    SCHOOL("학교"),
    HAKWON("학원");

    private final String label;

    SchoolType(String label) {
        this.label = label;
    }

    /** 화면 표기용. 응답에 코드와 함께 실어 화면이 변환표를 따로 들지 않게 한다. */
    public String label() {
        return label;
    }
}
