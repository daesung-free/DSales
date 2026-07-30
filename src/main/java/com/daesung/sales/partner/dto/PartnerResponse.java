package com.daesung.sales.partner.dto;

import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.entity.PartnerType;

/** 거래처 응답 DTO. name=거래처명2(풀네임), cityName=도시명, name1=상호만, region=지역, clientCategory=거래처구분. */
public record PartnerResponse(Long id, String code, String name, String cityName, String name1,
                             String region, String clientCategory, PartnerType type) {
    public static PartnerResponse from(Partner p) {
        return new PartnerResponse(p.getId(), p.getCode(), p.getName(),
                p.getCityName(), p.getName1(), p.getRegion(), p.getClientCategory(), p.getType());
    }
}
