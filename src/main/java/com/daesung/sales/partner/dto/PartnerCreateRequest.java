package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

/** 거래처 등록 요청 DTO. type 미지정 시 NORMAL. */
public record PartnerCreateRequest(

        @Schema(description = "거래처코드(고유)", example = "P-LIB", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String code,

        @Schema(description = "거래처명2(합쳐진 풀네임)", example = "진주 이룸도서", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "도시명(예: 진주). DSRE CITY_NM", example = "진주")
        String cityName,

        @Schema(description = "거래처명1=상호만(예: 이룸도서). DSRE CUST_NM", example = "이룸도서")
        String name1,

        @Schema(description = "지역(관할)", example = "경남") String region,
        @Schema(description = "거래처구분(특약점/기타학원/B2B/대성/자사몰)", example = "특약점") String clientCategory,

        @Schema(description = "정산유형(NORMAL=정상판매, CONSIGN=후정산/위탁, 미지정 시 NORMAL)", example = "CONSIGN")
        PartnerType type,

        // ── 연락처·거래기간(30p 거래처관리, 레거시 custData 대응) ──
        @Schema(description = "사업자주민번호", example = "800101-1234567") String bossId,
        @Schema(description = "연락처1", example = "02-123-4567") String tel1,
        @Schema(description = "연락처2", example = "02-123-4568") String tel2,
        @Schema(description = "휴대폰번호", example = "010-1234-5678") String cellPhone,
        @Schema(description = "팩스번호", example = "02-123-4569") String fax,
        @Schema(description = "우편번호", example = "13588") String zip,
        @Schema(description = "관할지역 — '지역'과 별개 축", example = "경남권") String zone2,
        @Schema(description = "등록일(거래 시작)", example = "2026-01-01") LocalDate startDate,
        @Schema(description = "만료일 — 값이 있으면 만료 거래처로 분류", example = "2026-12-31") LocalDate endDate
) {
}
