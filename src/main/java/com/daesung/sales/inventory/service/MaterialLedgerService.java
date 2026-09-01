package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.MaterialLedgerResponse;
import com.daesung.sales.inventory.dto.MaterialLedgerRow;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.material.entity.MaterialBom;
import com.daesung.sales.material.repository.MaterialBomRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 제품수불부 <b>자재 상세</b>(11p 2단계 조회).
 * 근거: 발주처 「도서관리·제품수불부현황 데이터 구조 보완 요청안」(2026-08-31).
 *
 * <p>요약({@code /stock/ledger})은 세트·회차 단위로 이월/입고/매출…/현재재고를 낸다.
 * 이 상세는 그 한 행을 골랐을 때 <b>구성 자재가 몇 장 들어갔는지</b>를 낸다.
 *
 * <p>★<b>자재 재고가 아니다.</b> 원문: "자재 자체의 입고·이월을 반영한 재고 잔량이 아니라
 * 해당 세트 내 <b>소요량</b> 기준". 자재를 얼마나 들여왔는지는 묻지 않는다 —
 * 세트가 팔린 만큼 자재가 얼마나 쓰였는지를 묻는다.
 *
 * <p>계산은 문서의 예시 그대로다:
 * <pre>
 * SET 출고 500 · 1회 출고 100 · 시험지(1회 전용, 소요 1)
 *   → 세트 출고분 500×1 + 회차 단독 출고분 100×1 = 600
 * </pre>
 */
@Service
@RequiredArgsConstructor
public class MaterialLedgerService {

    private final MaterialBomRepository materialBomRepository;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final ProductRepository productRepository;

    @Transactional(readOnly = true)
    public MaterialLedgerResponse materials(Long setProductId, Long roundProductId,
                                            LocalDate fromDate, LocalDate toDate) {
        Product set = productRepository.findById(setProductId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "세트 상품이 없습니다. id=" + setProductId));

        List<MaterialBom> boms = materialBomRepository.findBySet(setProductId);
        // 회차를 골랐으면 그 회차 전용 + 공통만 남긴다(문서의 "1회 선택 시" 화면).
        if (roundProductId != null) {
            boms = boms.stream()
                    .filter(b -> b.isCommon()
                            || roundProductId.equals(b.getRoundProduct().getId()))
                    .toList();
        }

        // 출고수량은 세트와 각 회차를 한 번에 읽는다 — 자재마다 조회하면 같은 상품을 여러 번 센다.
        Set<Long> ids = new HashSet<>();
        ids.add(setProductId);
        boms.forEach(b -> {
            if (!b.isCommon()) {
                ids.add(b.getRoundProduct().getId());
            }
        });
        Map<Long, Long> consumed = consumedMap(ids, fromDate, toDate);
        long setQty = consumed.getOrDefault(setProductId, 0L);

        List<MaterialLedgerRow> rows = new ArrayList<>();
        for (MaterialBom b : boms) {
            long fromSet = setQty * b.getQtyPerSet();
            // ★공통 자재의 '회차 단독 출고분'은 0이다.
            //   회차만 단품으로 팔렸을 때 범용 자재(OMR 등)가 몇 개 필요한지 문서에 정의가 없다.
            //   임의로 곱하면 실제로 나가지 않은 자재를 소비한 것으로 만든다 → 확인 전까지 0.
            long fromRound = b.isCommon()
                    ? 0L
                    : consumed.getOrDefault(b.getRoundProduct().getId(), 0L) * b.getQtyPerSet();

            rows.add(new MaterialLedgerRow(
                    b.getMaterial().getId(), b.getMaterial().getCode(), b.getMaterial().getName(),
                    b.getMaterial().getMaterialType(),
                    b.isCommon() ? "공통" : b.getRoundProduct().getName(),
                    b.getQtyPerSet(), fromSet, fromRound, fromSet + fromRound));
        }

        String roundName = (roundProductId == null) ? null
                : productRepository.findById(roundProductId).map(Product::getName).orElse(null);
        return new MaterialLedgerResponse(fromDate, toDate, set.getId(), set.getCode(), set.getName(),
                roundProductId, roundName, setQty, rows);
    }

    /** 상품별 소요 기준 출고량(양수). 이벤트가 없는 상품은 맵에 없다 → 호출부가 0으로 읽는다. */
    private Map<Long, Long> consumedMap(Set<Long> productIds, LocalDate from, LocalDate to) {
        Map<Long, Long> m = new HashMap<>();
        if (productIds.isEmpty()) {
            return m;
        }
        for (Object[] r : inventoryTxnRepository.consumedQtyByProduct(productIds, from, to)) {
            m.put(((Number) r[0]).longValue(), ((Number) r[1]).longValue());
        }
        return m;
    }
}
