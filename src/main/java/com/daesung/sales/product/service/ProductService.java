package com.daesung.sales.product.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.dto.ProductUpdateRequest;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
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
                req.price(), req.taxFree(), req.grade(), req.useYnOrDefault());
        return ProductResponse.from(productRepository.save(product));
    }

    @Transactional
    public ProductResponse update(Long id, ProductUpdateRequest req) {
        Product product = getOrThrow(id);
        product.update(req.name(), req.contentType(), req.set(),
                req.price(), req.taxFree(), req.grade(), req.useYn());
        return ProductResponse.from(product);
    }

    @Transactional
    public void deactivate(Long id) {
        getOrThrow(id).deactivate();
    }

    private Product getOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품이 없습니다. id=" + id));
    }
}
