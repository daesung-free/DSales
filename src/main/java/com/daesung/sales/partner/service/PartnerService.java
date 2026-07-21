package com.daesung.sales.partner.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.dto.PartnerCreateRequest;
import com.daesung.sales.partner.dto.PartnerResponse;
import com.daesung.sales.partner.dto.PartnerUpdateRequest;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;

    public PageResponse<PartnerResponse> findAll(String keyword, Pageable pageable) {
        Page<Partner> page = (keyword == null || keyword.isBlank())
                ? partnerRepository.findAll(pageable)
                : partnerRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                        keyword, keyword, pageable);
        return PageResponse.of(page.map(PartnerResponse::from));
    }

    public PartnerResponse findById(Long id) {
        return PartnerResponse.from(getOrThrow(id));
    }

    @Transactional
    public PartnerResponse create(PartnerCreateRequest req) {
        partnerRepository.findByCode(req.code()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 거래처코드: " + req.code());
        });
        Partner partner = Partner.create(req.code(), req.name(), req.type());
        return PartnerResponse.from(partnerRepository.save(partner));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerUpdateRequest req) {
        Partner partner = getOrThrow(id);
        partner.update(req.name(), req.type());
        partner.updateCredit(req.assureAmount(), req.assureExpiry(), req.assureNote());
        return PartnerResponse.from(partner);
    }

    private Partner getOrThrow(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + id));
    }
}
