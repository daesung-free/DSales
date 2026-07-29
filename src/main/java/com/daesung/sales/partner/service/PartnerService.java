package com.daesung.sales.partner.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.dto.CollateralExpiryResponse;
import com.daesung.sales.partner.dto.PartnerCreateRequest;
import com.daesung.sales.partner.dto.PartnerResponse;
import com.daesung.sales.partner.dto.PartnerUpdateRequest;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
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

    /**
     * 담보 만기 알림: 기준일 대비 담보 만기일이 withinDays 이내(또는 이미 만료)인 거래처.
     * 만기일 오름차순, 상태(EXPIRED/IMMINENT) + 남은 일수 포함.
     */
    public CollateralExpiryResponse collateralExpiry(LocalDate asOf, int withinDays) {
        LocalDate reference = (asOf != null) ? asOf : LocalDate.now();
        LocalDate threshold = reference.plusDays(withinDays);
        List<CollateralExpiryResponse.Row> rows =
                partnerRepository.findByAssureExpiryLessThanEqualOrderByAssureExpiryAsc(threshold).stream()
                        .map(p -> {
                            long days = ChronoUnit.DAYS.between(reference, p.getAssureExpiry());
                            String status = (days < 0) ? "EXPIRED" : "IMMINENT";
                            return new CollateralExpiryResponse.Row(p.getId(), p.getCode(), p.getName(),
                                    p.getAssureExpiry(), p.getAssureAmount(), days, status);
                        })
                        .toList();
        return new CollateralExpiryResponse(reference, withinDays, rows);
    }

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
        partner.applyNames(req.cityName(), req.name1());
        return PartnerResponse.from(partnerRepository.save(partner));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerUpdateRequest req) {
        Partner partner = getOrThrow(id);
        partner.update(req.name(), req.cityName(), req.name1(), req.type());
        partner.updateCredit(req.assureAmount(), req.assureExpiry(), req.assureNote());
        partner.updateTaxInfo(req.bizNo(), req.bossName(), req.addr1(), req.addr2(),
                req.bizStatus(), req.bizItem(), req.email1(), req.email2());
        return PartnerResponse.from(partner);
    }

    private Partner getOrThrow(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + id));
    }
}
