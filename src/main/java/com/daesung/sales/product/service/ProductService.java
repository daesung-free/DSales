package com.daesung.sales.product.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.BomRegisterRequest;
import com.daesung.sales.product.dto.BomResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.dto.ProductUpdateRequest;
import com.daesung.sales.product.entity.BomItem;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.BomItemRepository;
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
                req.salesDivision(), req.ledgerVisibleOrDefault(), req.webVisibleOrDefault());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest req) {
        Product product = getOrThrow(id);
        product.update(req.name(), req.contentType(), req.set(),
                req.price(), req.taxFree(), req.grade(),
                req.catCode(), req.catName(), req.useYn(),
                req.salesDivision(), req.ledgerVisible(), req.webVisible());
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

    private Product getOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품이 없습니다. id=" + id));
    }
}
