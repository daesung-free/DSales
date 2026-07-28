package com.daesung.sales.product.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.dto.BomRegisterRequest;
import com.daesung.sales.product.dto.BomResponse;
import com.daesung.sales.product.dto.PartnerPriceRequest;
import com.daesung.sales.product.dto.PartnerPriceResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.dto.ProductUpdateRequest;
import com.daesung.sales.product.entity.BomItem;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.entity.ProductPartnerPrice;
import com.daesung.sales.product.repository.BomItemRepository;
import com.daesung.sales.product.repository.ProductPartnerPriceRepository;
import com.daesung.sales.product.repository.ProductRepository;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;
    private final BomItemRepository bomItemRepository;
    private final ProductPartnerPriceRepository partnerPriceRepository;
    private final PartnerRepository partnerRepository;

    public PageResponse<ProductResponse> findAll(String keyword, Pageable pageable) {
        Page<Product> page = (keyword == null || keyword.isBlank())
                ? productRepository.findAll(pageable)
                : productRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                        keyword, keyword, pageable);
        return PageResponse.of(page.map(ProductResponse::from));
    }

    public ProductResponse findById(Long id) {
        return ProductResponse.from(getOrThrow(id));
    }

    @Transactional
    public ProductResponse create(ProductCreateRequest req) {
        productRepository.findByCode(req.code()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 상품코드: " + req.code());
        });
        Product product = Product.create(
                req.code(), req.name(), req.contentType(), req.set(),
                req.price(), req.taxFree(), req.grade(),
                req.catCode(), req.catName(), req.useYnOrDefault(),
                req.salesDivision(), req.ledgerVisibleOrDefault(), req.webVisibleOrDefault(),
                req.stockManagedOrDefault());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest req) {
        Product product = getOrThrow(id);
        product.update(req.name(), req.contentType(), req.set(),
                req.price(), req.taxFree(), req.grade(),
                req.catCode(), req.catName(), req.useYn(),
                req.salesDivision(), req.ledgerVisible(), req.webVisible(), req.stockManaged());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivate(Long id) {
        getOrThrow(id).deactivate();
    }

    /** BOM 구성 등록(기존 구성 대체). 완제품은 세트(is_set)로 표시. */
    @Transactional
    public BomResponse registerBom(Long parentId, BomRegisterRequest req) {
        Product parent = getOrThrow(parentId);
        bomItemRepository.deleteByParentId(parentId);
        List<BomItem> saved = new ArrayList<>();
        for (BomRegisterRequest.Component c : req.components()) {
            if (c.childProductId().equals(parentId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "구성품이 완제품과 같을 수 없습니다.");
            }
            Product child = productRepository.findById(c.childProductId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "구성품 상품이 없습니다. id=" + c.childProductId()));
            saved.add(bomItemRepository.save(BomItem.create(parent, child, c.ratio())));
        }
        parent.markAsSet();
        return BomResponse.from(parent, saved);
    }

    public BomResponse getBom(Long parentId) {
        Product parent = getOrThrow(parentId);
        return BomResponse.from(parent, bomItemRepository.findByParentId(parentId));
    }

    // ── 거래처별 단가·노출 매핑(도서관리 3번째 탭) ─────────────────────────

    /** 특정 도서의 거래처별 단가·노출 매핑 목록. */
    public List<PartnerPriceResponse> getPartnerPrices(Long productId) {
        getOrThrow(productId);
        return partnerPriceRepository.findByProductId(productId).stream()
                .map(PartnerPriceResponse::from).toList();
    }

    /** 도서×거래처 매핑 단건 조회(매출등록 공급률 자동조회용). 없으면 404. */
    public PartnerPriceResponse getPartnerPrice(Long productId, Long partnerId) {
        return PartnerPriceResponse.from(partnerPriceRepository
                .findByProductIdAndPartnerId(productId, partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처별 단가 매핑이 없습니다. product=" + productId + ", partner=" + partnerId)));
    }

    /** 도서×거래처 매핑 등록/수정(upsert). 도서·거래처 존재 검증. */
    @Transactional
    public PartnerPriceResponse upsertPartnerPrice(Long productId, Long partnerId, PartnerPriceRequest req) {
        Product product = getOrThrow(productId);
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + partnerId));
        ProductPartnerPrice mapping = partnerPriceRepository
                .findByProductIdAndPartnerId(productId, partnerId)
                .orElseGet(() -> ProductPartnerPrice.create(product, partner, null, true));
        mapping.update(req.supplyRate(), req.visibleOrDefault());
        return PartnerPriceResponse.from(partnerPriceRepository.save(mapping));
    }

    /** 도서×거래처 매핑 삭제. */
    @Transactional
    public void deletePartnerPrice(Long productId, Long partnerId) {
        ProductPartnerPrice mapping = partnerPriceRepository
                .findByProductIdAndPartnerId(productId, partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처별 단가 매핑이 없습니다. product=" + productId + ", partner=" + partnerId));
        partnerPriceRepository.delete(mapping);
    }

    private Product getOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품이 없습니다. id=" + id));
    }
}
