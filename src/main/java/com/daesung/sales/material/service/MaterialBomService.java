package com.daesung.sales.material.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.material.dto.MaterialBomRequest;
import com.daesung.sales.material.dto.MaterialBomResponse;
import com.daesung.sales.material.entity.MaterialBom;
import com.daesung.sales.material.repository.MaterialBomRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 세트·회차 ↔ 자재 매칭(33p). 근거: 발주처 구조보완요청안(2026-08-31).
 *
 * <p>★같은 자재를 <b>여러 세트에 중복 매칭</b>할 수 있다 — 그게 이 구조를 만든 이유다
 * ("국어 OMR 1개 자재를 여러 시행의 여러 회차에 매칭"). 다만 <b>같은 세트 안에서</b>
 * 같은 (회차, 자재) 조합은 한 번뿐이다. 두 번 넣으면 소요수량이 두 배가 된다.
 */
@Service
@RequiredArgsConstructor
public class MaterialBomService {

    private final MaterialBomRepository materialBomRepository;
    private final MaterialService materialService;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public MaterialBomResponse list(Long setProductId) {
        Product set = productOrThrow(setProductId);
        return MaterialBomResponse.of(set.getId(), set.getCode(), set.getName(),
                materialBomRepository.findBySet(setProductId));
    }

    /**
     * 매칭 추가. 같은 (세트, 회차, 자재)가 이미 있으면 <b>수량을 갱신</b>한다(upsert).
     *
     * <p>‼️공통 매칭(회차 null)은 DB UNIQUE가 막지 못한다 — MySQL은 NULL을 서로 다른
     * 값으로 보기 때문이다. 그래서 여기서 먼저 조회해 걸러낸다.
     */
    @Transactional
    public MaterialBomResponse add(Long setProductId, MaterialBomRequest req) {
        Product set = productOrThrow(setProductId);
        Product round = (req.roundProductId() == null) ? null : productOrThrow(req.roundProductId());
        if (round != null && round.getId().equals(set.getId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "회차가 세트 자신입니다. 세트 전체에 붙는 자재라면 회차를 비워 '공통'으로 등록하세요.");
        }

        materialBomRepository.findMatch(setProductId, req.roundProductId(), req.materialId())
                .ifPresentOrElse(
                        existing -> existing.updateQty(req.qtyPerSet(), req.perRound()),
                        () -> materialBomRepository.save(MaterialBom.of(set, round,
                                materialService.getOrThrow(req.materialId()), req.qtyPerSet(), req.perRound())));

        return list(setProductId);
    }

    /** 매칭 해제. */
    @Transactional
    public MaterialBomResponse remove(Long setProductId, Long bomId) {
        MaterialBom b = materialBomRepository.findById(bomId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "자재 매칭이 없습니다. id=" + bomId));
        if (!b.getSetProduct().getId().equals(setProductId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "다른 세트의 매칭입니다. setProductId=" + setProductId);
        }
        materialBomRepository.delete(b);
        return list(setProductId);
    }

    /** 자재 삭제 전 매칭 건수(삭제 차단 판단). */
    @Transactional(readOnly = true)
    public long matchedCount(Long materialId) {
        return materialBomRepository.countByMaterialId(materialId);
    }

    /** 세트의 매칭 전체(수불부 자재 상세 집계에서 재사용). */
    @Transactional(readOnly = true)
    public List<MaterialBom> bomsOf(Long setProductId) {
        return materialBomRepository.findBySet(setProductId);
    }

    private Product productOrThrow(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "상품이 없습니다. id=" + id));
    }
}
