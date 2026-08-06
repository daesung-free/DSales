package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.entity.PartnerType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;

/**
 * 거래처 응답 DTO. 30p 거래처관리 화면 컬럼 전체.
 * name=거래처명2(풀네임), name1=신고명, cityName=도시명, region=지역(zone1), zone2=관할지역.
 */
public record PartnerResponse(
        Long id,
        @Schema(description = "거래처코드") String code,
        @Schema(description = "거래처명(풀네임)") String name,
        @Schema(description = "도시명") String cityName,
        @Schema(description = "신고명(상호만)") String name1,
        @Schema(description = "지역") String region,
        @Schema(description = "관할지역") String zone2,
        @Schema(description = "거래처구분") String clientCategory,
        PartnerType type,
        @Schema(description = "사업자번호") String bizNo,
        @Schema(description = "사업자명(대표자)") String bossName,
        @Schema(description = "사업자주민번호") String bossId,
        @Schema(description = "업종") String bizStatus,
        @Schema(description = "업태") String bizItem,
        @Schema(description = "연락처1") String tel1,
        @Schema(description = "연락처2") String tel2,
        @Schema(description = "휴대폰번호") String cellPhone,
        @Schema(description = "팩스번호") String fax,
        @Schema(description = "이메일1") String email1,
        @Schema(description = "이메일2") String email2,
        @Schema(description = "우편번호") String zip,
        @Schema(description = "기본주소") String addr1,
        @Schema(description = "상세주소") String addr2,
        @Schema(description = "등록일(거래 시작)") LocalDate startDate,
        @Schema(description = "만료일 — 값이 있으면 만료 거래처") LocalDate endDate,
        @Schema(description = "만료 여부") boolean expired,
        @Schema(description = "담보금액") Long assureAmount,
        @Schema(description = "담보만기") LocalDate assureExpiry,
        @Schema(description = "담보내용") String assureNote
) {
    public static PartnerResponse from(Partner p) {
        return new PartnerResponse(p.getId(), p.getCode(), p.getName(), p.getCityName(), p.getName1(),
                p.getRegion(), p.getZone2(), p.getClientCategory(), p.getType(),
                p.getBizNo(), p.getBossName(), p.getBossId(), p.getBizStatus(), p.getBizItem(),
                p.getTel1(), p.getTel2(), p.getCellPhone(), p.getFax(), p.getEmail1(), p.getEmail2(),
                p.getZip(), p.getAddr1(), p.getAddr2(),
                p.getStartDate(), p.getEndDate(), p.isExpired(),
                p.getAssureAmount(), p.getAssureExpiry(), p.getAssureNote());
    }
}
