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

        List<MaterialBom> all = materialBomRepository.findBySet(setProductId);

        // ★회차 수는 **거르기 전에** 센다. 공통 반복형 자재의 회차당 소요량이
        //   "세트당수량 ÷ 회차수"라서, 1회만 골랐다고 회차수가 1이 되면 OMR이 4장으로 뻥튀기된다.
        Set<Long> allRoundIds = new HashSet<>();
        all.forEach(b -> {
            if (!b.isCommon()) {
                allRoundIds.add(b.getRoundProduct().getId());
            }
        });
        int roundCount = allRoundIds.size();

        List<MaterialBom> boms = all;
        // 회차를 골랐으면 그 회차 전용 + 공통만 남긴다(문서의 "1회 선택 시" 화면).
        if (roundProductId != null) {
            boms = all.stream()
                    .filter(b -> b.isCommon()
                            || roundProductId.equals(b.getRoundProduct().getId()))
                    .toList();
        }

        // 공통 자재가 볼 '회차 단독 출고'의 범위: 회차를 골랐으면 그 회차만, 아니면 세트의 모든 회차.
        Set<Long> commonRoundScope = (roundProductId != null)
                ? Set.of(roundProductId) : allRoundIds;

        // 출고수량은 세트와 각 회차를 한 번에 읽는다 — 자재마다 조회하면 같은 상품을 여러 번 센다.
        Set<Long> ids = new HashSet<>();
        ids.add(setProductId);
        ids.addAll(allRoundIds);        // 공통 자재가 모든 회차의 단독출고를 봐야 한다
        Map<Long, Long> consumed = consumedMap(ids, fromDate, toDate);
        long setQty = consumed.getOrDefault(setProductId, 0L);

        List<MaterialLedgerRow> rows = new ArrayList<>();
        for (MaterialBom b : boms) {
            long fromSet = setQty * b.getQtyPerSet();
            long fromRound = roundConsumption(b, roundCount, commonRoundScope, consumed);

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

    /**
     * <b>회차 단독 출고분</b> — 세트를 사지 않고 회차만 팔린 만큼 나간 자재 수량.
     *
     * <p>회차 전용 자재(시험지·해설지)는 간단하다: 그 회차의 단독출고 × 세트당수량.
     *
     * <p>★<b>공통 자재</b>가 문제였다. 구조보완요청안 각주가 공통 자재를 두 종류로 나눠 놓았는데
     * ("회차마다 반복 사용되는 자재(OMR, 4회차 기준 <b>4</b>)는 회차 수만큼 반영한 값을,
     * 세트 전체에 한 번만 필요한 자재(쿠폰)는 <b>1</b>로 고정") 숫자만으로는 구분이 안 된다 —
     * {@code 4}가 "4회차 × 1"인지 "세트당 4개 고정"인지 알 수 없었다.
     * 그래서 {@code per_round} 플래그(V61)를 두고 이제 이렇게 나눈다.
     * <pre>
     *   반복형  회차당 = 세트당수량 ÷ 회차수 (OMR 4 ÷ 4회차 = 1) → 회차 단독출고에도 나간다
     *   1회형   0 — 세트를 사야 붙는 자재라 회차만 사면 나가지 않는다
     * </pre>
     */
    private long roundConsumption(MaterialBom b, int roundCount,
                                  Set<Long> commonRoundScope, Map<Long, Long> consumed) {
        if (!b.isCommon()) {
            return consumed.getOrDefault(b.getRoundProduct().getId(), 0L) * b.getQtyPerSet();
        }
        long perRound = b.qtyPerRound(roundCount);
        if (perRound == 0) {
            return 0L;      // 1회형이거나 회차가 없다
        }
        long qty = 0;
        for (Long rid : commonRoundScope) {
            qty += consumed.getOrDefault(rid, 0L) * perRound;
        }
        return qty;
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
