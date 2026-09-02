package com.daesung.sales.product.service;

import com.daesung.sales.audit.entity.MasterEntityType;
import com.daesung.sales.audit.service.MasterChangeLogService;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.dto.PartnerSupplyRateBulkRequest;
import com.daesung.sales.product.dto.PartnerSupplyRateBulkResult;
import com.daesung.sales.product.dto.PartnerSupplyRateRequest;
import com.daesung.sales.product.dto.PartnerSupplyRateResponse;
import com.daesung.sales.product.entity.MajorCategory;
import com.daesung.sales.product.entity.PartnerSupplyRate;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.entity.SalesDivision;
import com.daesung.sales.product.repository.PartnerSupplyRateRepository;
import com.daesung.sales.product.repository.SalesDivisionRepository;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래처별 대분류 공급률·노출(34p) 관리. 근거: 정본 34p
 * "거래처별로 상품군(대분류)마다 공급률·노출여부를 사전 설정하여 주문/출고 및 신청사이트에 자동 반영".
 *
 * <p>★축이 <b>거래처 × 대분류</b>다. 예전 구현(V19)은 도서 × 거래처였는데, 그러면 담당자가
 * 도서 한 권마다 거래처 전부를 깔아야 하고 신간이 들어올 때마다 같은 일을 반복해야 한다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerSupplyRateService {

    private final PartnerSupplyRateRepository rateRepository;
    private final PartnerRepository partnerRepository;
    private final SalesDivisionRepository salesDivisionRepository;
    private final MasterChangeLogService masterChangeLogService;
    private final CurrentAuditor currentAuditor;

    /** 34p 목록. 거래처구분·거래처·대분류로 좁힌다(전부 선택사항). */
    public List<PartnerSupplyRateResponse> search(Long partnerId, MajorCategory majorCategory,
                                                  String clientCategory) {
        return rateRepository.search(partnerId, majorCategory, clientCategory).stream()
                .map(PartnerSupplyRateResponse::from).toList();
    }

    /**
     * 매출등록·매출업로드 공용 — 이 거래처가 이 상품에 대해 갖는 공급률.
     *
     * <p>상품의 세부구분 → 대분류를 따라가 매핑을 찾는다. 없으면 null을 돌려주고,
     * 호출부가 <b>도서 기본정보의 공급률</b>로 넘어간다(정본 34p "매핑이 없는 거래처는
     * 기본정보 탭의 공급률이 기본값"). 사용여부가 꺼진 매핑은 없는 것으로 본다.
     *
     * @return 공급률(%) 또는 null(매핑 없음·세부구분 미지정)
     */
    public Integer rateFor(Product product, Long partnerId) {
        MajorCategory major = majorOf(product);
        if (major == null) {
            return null;
        }
        return rateRepository.findActive(partnerId, major)
                .map(PartnerSupplyRate::getSupplyRate).orElse(null);
    }

    /**
     * 이 거래처가 이 상품에 대해 갖는 <b>권당 할인액</b>(34p).
     *
     * <p>있으면 금액이 공급률 대신 이 값으로 계산된다(정가−할인액)×수량.
     * 근거: 레거시 {@code 매출가져오기.vb:425}.
     *
     * @return 할인액 또는 null(매핑 없음·미설정·0)
     */
    public Integer discountFor(Product product, Long partnerId) {
        MajorCategory major = majorOf(product);
        if (major == null) {
            return null;
        }
        return rateRepository.findActive(partnerId, major)
                .map(PartnerSupplyRate::getDiscountAmount)
                .filter(d -> d > 0)
                .orElse(null);
    }

    /** 상품의 대분류 — 세부구분 마스터를 거쳐 파생. 미지정이면 null. */
    private MajorCategory majorOf(Product product) {
        String code = product.getSalesDivision();
        if (code == null || code.isBlank()) {
            return null;
        }
        return salesDivisionRepository.findByCode(code)
                .map(SalesDivision::getMajorCategory).orElse(null);
    }

    /** 단건 조회(사용여부 무관 — 꺼둔 매핑도 관리 화면에서 고칠 수 있어야 한다). 없으면 404. */
    public PartnerSupplyRateResponse get(Long partnerId, MajorCategory majorCategory) {
        return PartnerSupplyRateResponse.from(getOrThrow(partnerId, majorCategory));
    }

    /** 매핑 존재 여부. 업로드에서 신규/수정을 나눠 세는 데 쓴다(숫자만 봐도 사고를 알아챈다). */
    @Transactional(readOnly = true)
    public boolean exists(Long partnerId, MajorCategory majorCategory) {
        return rateRepository.findByPartnerIdAndMajorCategory(partnerId, majorCategory).isPresent();
    }

    /** 등록/수정(upsert). 거래처 존재 검증. */
    @Transactional
    public PartnerSupplyRateResponse upsert(Long partnerId, MajorCategory majorCategory,
                                            PartnerSupplyRateRequest req) {
        Partner partner = partner(partnerId);
        PartnerSupplyRate mapping = rateRepository
                .findByPartnerIdAndMajorCategory(partnerId, majorCategory)
                .orElseGet(() -> PartnerSupplyRate.create(partner, majorCategory, null, null, true, true));
        // 기존 매핑이 있을 때만 '변경'이다. 신규 생성은 created_by/created_at이 이미 답한다.
        Map<String, String> before = (mapping.getId() == null) ? null : snapshot(mapping);
        mapping.update(req.supplyRate(), req.discountAmount(), req.webVisible(), req.useYn());
        PartnerSupplyRate saved = rateRepository.save(mapping);
        if (before != null) {
            masterChangeLogService.recordDiff(MasterEntityType.PARTNER_PRICE, saved.getId(),
                    partner.getCode() + "/" + majorCategory.label(), before, snapshot(saved));
        }
        return PartnerSupplyRateResponse.from(saved);
    }

    /**
     * 일괄 적용. 정본 34p "여러 거래처를 체크해 '선택 거래처 일괄 적용'으로
     * 공급률·Web게시여부·할인액을 일괄 반영".
     *
     * <p>기본은 <b>기존 매핑을 건드리지 않는다</b>(overwrite=false). 예외 단가를 따로 넣어둔
     * 거래처가 일괄 적용 한 번에 조용히 덮이면, 그 거래처 매출이 잘못된 금액으로 등록된다.
     * 덮어쓸지는 담당자가 명시적으로 고르게 하고, 건드리지 않은 거래처는 코드까지 돌려준다.
     */
    @Transactional
    public PartnerSupplyRateBulkResult bulkApply(PartnerSupplyRateBulkRequest req) {
        int created = 0;
        int updated = 0;
        List<String> skipped = new ArrayList<>();

        for (Long partnerId : req.partnerIds().stream().distinct().toList()) {
            Partner partner = partner(partnerId);
            var existing = rateRepository.findByPartnerIdAndMajorCategory(partnerId, req.majorCategory());
            if (existing.isPresent()) {
                if (!req.overwriteOrDefault()) {
                    skipped.add(partner.getCode());
                    continue;
                }
                existing.get().update(req.supplyRate(), req.discountAmount(),
                        req.webVisible(), req.useYn());
                rateRepository.save(existing.get());
                updated++;
            } else {
                PartnerSupplyRate m = PartnerSupplyRate.create(partner, req.majorCategory(),
                        req.supplyRate(), req.discountAmount(), req.webVisible(), req.useYn());
                rateRepository.save(m);
                created++;
            }
        }
        return new PartnerSupplyRateBulkResult(req.majorCategory(), req.majorCategory().label(),
                created, updated, skipped.size(), skipped);
    }

    /** 삭제(논리삭제 — 행은 남고 삭제자·시각이 기록된다). 지우면 도서 기본 공급률이 바탕값이 된다. */
    @Transactional
    public void delete(Long partnerId, MajorCategory majorCategory) {
        getOrThrow(partnerId, majorCategory).markDeleted(currentAuditor.username());
    }

    private PartnerSupplyRate getOrThrow(Long partnerId, MajorCategory majorCategory) {
        return rateRepository.findByPartnerIdAndMajorCategory(partnerId, majorCategory)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처별 공급률 매핑이 없습니다. partner=" + partnerId
                                + ", major=" + majorCategory));
    }

    private Partner partner(Long partnerId) {
        return partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + partnerId));
    }

    private static Map<String, String> snapshot(PartnerSupplyRate m) {
        Map<String, String> s = new LinkedHashMap<>();
        s.put("supplyRate|공급률", str(m.getSupplyRate()));
        s.put("discountAmount|할인액", str(m.getDiscountAmount()));
        s.put("webVisible|Web게시", String.valueOf(m.isWebVisible()));
        s.put("useYn|사용여부", String.valueOf(m.isUseYn()));
        return s;
    }

    private static String str(Object v) {
        return (v == null) ? null : String.valueOf(v);
    }
}
