package com.daesung.sales.partner.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.dto.PartnerCreateRequest;
import com.daesung.sales.partner.dto.PartnerResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;

    public PageResponse<PartnerResponse> findAll(Pageable pageable) {
        return PageResponse.of(partnerRepository.findAll(pageable).map(PartnerResponse::from));
    }

    public PartnerResponse findById(Long id) {
        Partner partner = partnerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + id));
        return PartnerResponse.from(partner);
    }

    @Transactional
    public PartnerResponse create(PartnerCreateRequest req) {
        partnerRepository.findByCode(req.code()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 거래처코드: " + req.code());
        });
        Partner partner = Partner.create(req.code(), req.name(), req.type());
        return PartnerResponse.from(partnerRepository.save(partner));
    }
}
