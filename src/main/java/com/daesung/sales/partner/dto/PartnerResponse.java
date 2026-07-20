package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.entity.PartnerType;

/** 거래처 응답 DTO. */
public record PartnerResponse(Long id, String code, String name, PartnerType type) {
    public static PartnerResponse from(Partner p) {
        return new PartnerResponse(p.getId(), p.getCode(), p.getName(), p.getType());
    }
}
