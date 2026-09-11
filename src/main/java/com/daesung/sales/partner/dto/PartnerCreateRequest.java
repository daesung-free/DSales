package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.PartnerType;
import java.time.LocalDate;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

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

        // ── 사업자 정보(30p 거래처관리) ──
        //    ★등록 때부터 받는다. 예전에는 수정에만 있어, 새 거래처를 만들면
        //      계산서·거래명세서에 찍힐 사업자번호가 비어 있는 채로 남았다.
        @Schema(description = """
                사업자번호. `123-45-67890` 또는 `1234567890`.
                ‼️형식만 본다 — 국세청 검증은 하지 않는다. 계산서·거래명세서에 그대로 찍히는 값이라
                자릿수가 안 맞으면 문서가 잘못 나간다.""", example = "123-45-67890")
        @Pattern(regexp = "^$|^\\d{3}-?\\d{2}-?\\d{5}$",
                message = "사업자번호는 10자리 숫자여야 합니다(예: 123-45-67890)")
        String bizNo,

        @Schema(description = "대표자 성명", example = "홍길동") String bossName,
        @Schema(description = "주소", example = "서울시 강남구") String addr1,
        @Schema(description = "상세주소", example = "테헤란로 1") String addr2,
        @Schema(description = "업태", example = "도소매") String bizStatus,
        @Schema(description = "종목", example = "서적") String bizItem,

        @Schema(description = "이메일1", example = "a@b.com")
        @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식이 아닙니다")
        String email1,

        @Schema(description = "이메일2")
        @Pattern(regexp = "^$|^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$", message = "이메일 형식이 아닙니다")
        String email2,

        // ── 연락처·거래기간(30p 거래처관리, 레거시 custData 대응) ──
        @Schema(description = "사업자주민번호", example = "800101-1234567") String bossId,
        @Schema(description = "연락처1", example = "02-123-4567") String tel1,
        @Schema(description = "연락처2", example = "02-123-4568") String tel2,
        @Schema(description = "휴대폰번호", example = "010-1234-5678") String cellPhone,
        @Schema(description = "팩스번호", example = "02-123-4569") String fax,
        @Schema(description = "우편번호", example = "13588") String zip,
        @Schema(description = "관할지역 — '지역'과 별개 축", example = "경남권") String zone2,
        @Schema(description = "등록일(거래 시작)", example = "2026-01-01") LocalDate startDate,
        @Schema(description = "만료일 — 값이 있으면 만료 거래처로 분류", example = "2026-12-31") LocalDate endDate,

        // ── 담보(여신) ──
        // ‼️화면 신규등록에는 담보 입력란이 있는데 여기 필드가 없어 **입력값이 통째로 버려졌다**
        //   (2026-09-11 점검에서 발견 — 담보 500만원을 넣고 등록해도 null 로 저장됐다).
        //   담보가 비면 외상매출현황의 담보비율·경고등급이 전부 안 나온다.
        @Schema(description = "담보금액(여신한도)", example = "5000000") Long assureAmount,
        @Schema(description = "담보 만기", example = "2027-01-01") LocalDate assureExpiry,
        @Schema(description = "담보 내용", example = "부동산 근저당") String assureNote
) {
}
