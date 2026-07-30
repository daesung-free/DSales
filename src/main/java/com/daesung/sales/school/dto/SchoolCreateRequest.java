package com.daesung.sales.school.dto;

import com.daesung.sales.school.entity.SchoolType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 학교 등록 요청(35p). 학교코드=거래처코드 동일값. */
public record SchoolCreateRequest(

        @Schema(description = "학교코드(=거래처코드 동일값)", example = "A0003", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String schoolCode,

        @Schema(description = "거래처코드(partner.code 매핑)", example = "A0003") String custCode,
        @Schema(description = "거래처명", example = "진주 이룸도서") String custName,
        @Schema(description = "도시", example = "진주") String city,
        @Schema(description = "지역(관할)", example = "경남") String region,
        @Schema(description = "학교/학원명", example = "진주고등학교") String schoolName,
        @Schema(description = "학교 Y/N(기본 true)", example = "true") Boolean isSchool,
        @Schema(description = "학교/학원구분(SCHOOL/HAKWON, 기본 SCHOOL)", example = "SCHOOL") SchoolType schoolType,
        @Schema(description = "거래처구분(특약점/기타학원/B2B 등)", example = "특약점") String clientCategory,
        @Schema(description = "메모") String memo
) {
}
