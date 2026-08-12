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
import java.util.Comparator;
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
     *
     * <p>정렬은 <b>만료(EXPIRED) 먼저, 그 다음 임박(IMMINENT)</b>이고 각 묶음 안에서는 만기일 순이다.
     * 근거: 발주처 확정(자료요청서 3-2(바)) — "만기 경과 건은 빨간색 표시 후 <b>별도 목록으로 쏘팅</b>".
     * 만기일 순으로만 두면 이미 지난 건이 목록 위쪽에 섞여, 아직 시간이 있는 건과 구분이 안 된다.
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
                        // 만료분을 앞으로(경과일이 큰 순), 그 뒤에 임박분(만기일 가까운 순).
                        .sorted(Comparator
                                .comparing((CollateralExpiryResponse.Row r) -> !"EXPIRED".equals(r.status()))
                                .thenComparing(CollateralExpiryResponse.Row::assureExpiry))
                        .toList();
        return new CollateralExpiryResponse(reference, withinDays, rows);
    }

    /**
     * 거래처 목록. 레거시 거래처관리와 동일하게 <b>거래중(만료일 없음)만 기본 노출</b>하고,
     * includeExpired=true면 만료된 거래처까지 보여준다(화면의 '만료된 거래처 포함' 체크박스).
     * 근거: 거래처관리.vb — {@code where len(endDate) = 0} / {@code >= 0} 분기.
     */
    public PageResponse<PartnerResponse> findAll(String keyword, boolean includeExpired, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResponse.of(partnerRepository.search(kw, includeExpired, pageable)
                .map(PartnerResponse::from));
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
        partner.applyNames(req.cityName(), req.name1(), req.region(), req.clientCategory());
        partner.updateContact(req.bossId(), req.tel1(), req.tel2(), req.cellPhone(), req.fax(),
                req.zip(), req.zone2(), req.startDate(), req.endDate());
        return PartnerResponse.from(partnerRepository.save(partner));
    }

    @Transactional
    public PartnerResponse update(Long id, PartnerUpdateRequest req) {
        Partner partner = getOrThrow(id);
        partner.update(req.name(), req.cityName(), req.name1(), req.region(), req.clientCategory(), req.type());
        partner.updateCredit(req.assureAmount(), req.assureExpiry(), req.assureNote());
        partner.updateTaxInfo(req.bizNo(), req.bossName(), req.addr1(), req.addr2(),
                req.bizStatus(), req.bizItem(), req.email1(), req.email2());
        partner.updateContact(req.bossId(), req.tel1(), req.tel2(), req.cellPhone(), req.fax(),
                req.zip(), req.zone2(), req.startDate(), req.endDate());
        return PartnerResponse.from(partner);
    }

    private Partner getOrThrow(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + id));
    }
}
