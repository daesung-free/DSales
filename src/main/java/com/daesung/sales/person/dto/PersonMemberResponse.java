package com.daesung.sales.person.dto;

import com.daesung.sales.person.entity.PersonMember;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;

/** 개인회원 응답. 레거시 개인회원관리 화면 컬럼과 1:1. */
public record PersonMemberResponse(
        @Schema(description = "id") Long id,
        @Schema(description = "연도") String fiscalYear,
        @Schema(description = "등록일") LocalDateTime inputDate,
        @Schema(description = "결제일") LocalDateTime payDate,
        @Schema(description = "학생ID") String studentId,
        @Schema(description = "학생이름") String studentName,
        @Schema(description = "상품코드") String goodsCode,
        @Schema(description = "상품명") String goodsName,
        @Schema(description = "우편번호") String post,
        @Schema(description = "주소1") String addr1,
        @Schema(description = "주소2") String addr2,
        @Schema(description = "수취인명") String receiver,
        @Schema(description = "연락처1") String tel1,
        @Schema(description = "연락처2") String tel2,
        @Schema(description = "메모") String memo,
        @Schema(description = "관리(담당)") String manager
) {
    public static PersonMemberResponse from(PersonMember m) {
        return new PersonMemberResponse(m.getId(), m.getFiscalYear(), m.getInputDate(), m.getPayDate(),
                m.getStudentId(), m.getStudentName(), m.getGoodsCode(), m.getGoodsName(),
                m.getPost(), m.getAddr1(), m.getAddr2(), m.getReceiver(),
                m.getTel1(), m.getTel2(), m.getMemo(), m.getManager());
    }
}
