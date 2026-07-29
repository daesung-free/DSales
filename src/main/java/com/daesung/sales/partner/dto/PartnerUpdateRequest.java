package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDate;

/** 거래처 수정 요청 DTO. 코드(code)는 불변이라 제외. 담보(여신)는 채권 담보비율 계산에 사용. */
public record PartnerUpdateRequest(

        @Schema(description = "거래처명2(합쳐진 풀네임)", example = "진주 이룸도서", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String name,

        @Schema(description = "도시명(예: 진주). DSRE CITY_NM", example = "진주")
        String cityName,

        @Schema(description = "거래처명1=상호만(예: 이룸도서). DSRE CUST_NM", example = "이룸도서")
        String name1,

        @Schema(description = "정산유형(NORMAL/CONSIGN)", example = "CONSIGN")
        PartnerType type,

        @Schema(description = "담보금액(여신한도)", example = "50000000")
        Long assureAmount,

        @Schema(description = "담보 만기", example = "2027-06-30")
        LocalDate assureExpiry,

        @Schema(description = "담보 내용(비고)", example = "부동산 근저당")
        String assureNote,

        @Schema(description = "사업자번호", example = "123-45-67890") String bizNo,
        @Schema(description = "대표자 성명", example = "홍길동") String bossName,
        @Schema(description = "주소", example = "서울시 강남구") String addr1,
        @Schema(description = "상세주소", example = "테헤란로 1") String addr2,
        @Schema(description = "업태", example = "도소매") String bizStatus,
        @Schema(description = "종목", example = "서적") String bizItem,
        @Schema(description = "이메일1", example = "a@b.com") String email1,
        @Schema(description = "이메일2") String email2
) {
}
