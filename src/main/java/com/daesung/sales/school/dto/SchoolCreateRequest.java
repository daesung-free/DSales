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
        @Schema(description = "메모") String memo,

        // ── 학교/학원검색(29p) 축 — DSRE 동기화가 주지 않아 수기로 받는다 ──
        @Schema(description = "지역코드(레거시 cityCode). 검색 필터에 쓰인다", example = "11") String cityCode,
        @Schema(description = "특약점L — 특약점 소재. 검색의 '특약점LN' = 이 값 + 특약점명", example = "강남")
        String partnerLoc,
        @Schema(description = """
                모의고사 담당 특약점명. ★**같은 학교라도 상품군에 따라 담당이 다르다** —
                대표 특약점(custName) 하나로는 표현되지 않는다.""")
        String mockPartnerName,
        @Schema(description = "모의고사 담당 특약점코드") String mockPartnerCode,
        @Schema(description = "IC 담당 특약점명. 모의고사와 같은 이유로 따로 든다") String icPartnerName,
        @Schema(description = "IC 담당 특약점코드") String icPartnerCode
) {
}
