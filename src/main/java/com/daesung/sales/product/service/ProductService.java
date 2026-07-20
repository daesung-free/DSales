package com.daesung.sales.product.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.product.dto.ProductCreateRequest;
import com.daesung.sales.product.dto.ProductResponse;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    public PageResponse<ProductResponse> findAll(Pageable pageable) {
        return PageResponse.of(productRepository.findAll(pageable).map(ProductResponse::from));
    }

    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품이 없습니다. id=" + id));
        return ProductResponse.from(product);
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
}
