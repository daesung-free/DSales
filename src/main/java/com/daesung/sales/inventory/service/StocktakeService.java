package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.StocktakeRequest;
import com.daesung.sales.inventory.dto.StocktakeResponse;
import com.daesung.sales.inventory.entity.Stocktake;
import com.daesung.sales.inventory.repository.StocktakeRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 재고실사(신규, §7G). 실물 카운트를 시스템(캐시)과 대조 → 차이(diff)만큼 ADJUST 이벤트로 조정.
 * 조정 후 캐시=실물이 되고, 수불부(단일공식)에도 조정이 반영된다. 실사 이력(Stocktake) 보존.
 */
@Service
@RequiredArgsConstructor
public class StocktakeService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final StocktakeRepository stocktakeRepository;
    private final WarehouseRepository warehouseRepository;
    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    /** 재고실사 등록+적용. 상품마다 대조 후 차이가 있으면 ADJUST 조정. 한 트랜잭션. */
    @Transactional
    public StocktakeResponse register(StocktakeRequest req) {
        Warehouse warehouse = warehouseRepository.findById(req.warehouseId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "창고가 없습니다. id=" + req.warehouseId()));
        String stocktakeNo = "ST-" + req.stocktakeDate().format(YYYYMMDD) + "-"
                + stocktakeRepository.nextStocktakeSeq();
        Stocktake stocktake = Stocktake.create(stocktakeNo, warehouse, req.stocktakeDate(), req.memo());

        List<StocktakeResponse.Line> lines = new ArrayList<>();
        int adjustedCount = 0;
        for (StocktakeRequest.Item item : req.items()) {
            Product product = productRepository.findById(item.productId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                            "상품이 없습니다. id=" + item.productId()));

            int systemQty = inventoryService.balanceOf(product.getId(), warehouse.getId());
            int diff = item.countedQty() - systemQty;
            stocktake.addLine(product, systemQty, item.countedQty());

            int newBalance = systemQty;
            if (diff != 0) {
                newBalance = inventoryService.adjust(product, warehouse, diff,
                        req.stocktakeDate(), stocktakeNo, "재고실사 조정");
                adjustedCount++;
            }
            lines.add(new StocktakeResponse.Line(product.getId(), product.getCode(), product.getName(),
                    systemQty, item.countedQty(), diff, newBalance));
        }
        stocktakeRepository.save(stocktake); // 헤더+라인 cascade

        return new StocktakeResponse(stocktake.getId(), stocktakeNo,
                warehouse.getId(), warehouse.getName(), req.stocktakeDate(), adjustedCount, lines);
    }
}
