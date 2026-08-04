package com.daesung.sales.person.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/** 개인회원 등록·수정 요청. 근거: 레거시 personData 컬럼. */
public record PersonMemberRequest(

        @Schema(description = "연도", example = "2026") String fiscalYear,

        @Schema(description = "결제일", example = "2026-06-15T10:30:00") LocalDateTime payDate,

        @Schema(description = "학생ID", example = "hong1234", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String studentId,

        @Schema(description = "학생이름", example = "홍길동", requiredMode = Schema.RequiredMode.REQUIRED)
        @NotBlank String studentName,

        @Schema(description = "상품코드", example = "ICF") String goodsCode,
        @Schema(description = "상품명", example = "2026년 IC파이널(국수영)") String goodsName,
        @Schema(description = "우편번호", example = "13588") String post,
        @Schema(description = "주소1", example = "경기 성남시 분당구 중앙공원로 1") String addr1,
        @Schema(description = "주소2", example = "101동 202호") String addr2,
        @Schema(description = "수취인명", example = "홍길동") String receiver,
        @Schema(description = "연락처1", example = "010-1234-5678") String tel1,
        @Schema(description = "연락처2", example = "02-123-4567") String tel2,
        @Schema(description = "메모") String memo,
        @Schema(description = "관리(담당)", example = "본사") String manager
) {
}
