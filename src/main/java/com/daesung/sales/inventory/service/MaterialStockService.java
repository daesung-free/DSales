package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.common.query.MultiSelect;
import com.daesung.sales.inventory.dto.MaterialDisposalRequest;
import com.daesung.sales.inventory.dto.MaterialDisposalResponse;
import com.daesung.sales.inventory.dto.MaterialInboundRequest;
import com.daesung.sales.inventory.dto.MaterialInboundResponse;
import com.daesung.sales.inventory.dto.MaterialRecordRow;
import com.daesung.sales.inventory.dto.MaterialStockRow;
import com.daesung.sales.inventory.dto.MaterialTransferRequest;
import com.daesung.sales.inventory.dto.MaterialTransferResponse;
import com.daesung.sales.inventory.entity.TxnType;
import com.daesung.sales.inventory.entity.InventoryTxn;
import com.daesung.sales.inventory.repository.InventoryRepository;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.material.entity.Material;
import com.daesung.sales.material.repository.MaterialRepository;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.MaterialType;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자재 재고 — 입고와 잔량 조회.
 *
 * <p>근거: 9/27 회의 A-1(항목 4·5·6·22) — 수불·자재를 매출프로그램이 <b>단독</b> 관리한다.
 * 그 전까지 자재는 마스터와 <b>소요량</b>만 있었고 잔량 개념이 없었다.
 *
 * <p>★<b>도서 재고와 같은 원장을 쓴다.</b> {@code inventory_txn} 한 표에 남기고
 * 잔량은 그 합이다(V78). 자재용 표를 따로 파면 입고·이고·폐기·실사·전표취소·마감잠금이
 * 전부 두 벌이 되고, 둘이 어긋나는 순간 "화면마다 재고가 다르다"는 레거시 문제가 그대로 돌아온다.
 *
 * <p>‼️<b>음수 재고는 막지 않는다</b>(발주처 지시로 전면 제거된 규칙, V56).
 * 도서와 같다 — 실물이 먼저 나가고 전표가 늦게 들어오는 실무가 있어서다.
 */
@Service
@RequiredArgsConstructor
public class MaterialStockService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final MaterialRepository materialRepository;
    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;
    private final InventoryRepository inventoryRepository;
    private final InventoryTxnRepository inventoryTxnRepository;
    private final SequenceService sequenceService;
    /** 잔량 증감 규칙(원자적 UPDATE·음수 허용)은 재고 엔진 한 곳에만 둔다 — 두 벌이 되면 언젠가 갈린다. */
    private final InventoryService inventoryService;

    /** 자재 입고. 품목마다 (1) 재고이벤트 INBOUND 기록 + (2) 잔량 가산을 한 트랜잭션으로. */
    @Transactional
    public MaterialInboundResponse inbound(MaterialInboundRequest req) {
        Warehouse warehouse = warehouse(req.destinationWarehouseId(), "창고");
        Partner supplier = partnerRepository.findById(req.supplierClientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.supplierClientId()));

        // 전표번호는 도서 입고와 같은 채번(IN-)을 쓴다. 되돌릴 때 "무슨 전표냐"를 한 곳에서 찾는다.
        String inboundNo = "IN-" + req.processedDate().format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_INBOUND);

        List<MaterialInboundResponse.Line> lines = new ArrayList<>();
        for (MaterialInboundRequest.Item item : req.items()) {
            Material material = material(item.materialId());

            inventoryTxnRepository.save(InventoryTxn.materialInbound(material, warehouse,
                    item.qty(), item.unitCost(), req.inboundType(), req.processedDate(),
                    supplier, item.memo(), inboundNo));

            int currentQty = inventoryService.applyMaterialDelta(material, warehouse, item.qty());
            lines.add(new MaterialInboundResponse.Line(material.getId(), material.getCode(),
                    material.getName(), item.qty(), currentQty));
        }
        return new MaterialInboundResponse(inboundNo, warehouse.getId(), warehouse.getName(), lines);
    }

    /**
     * 자재 이고(창고 이동). 출발 −qty · 도착 +qty 를 한 트랜잭션으로, 전표번호(TR-)를 붙인다.
     *
     * <p>전표번호가 있어야 되돌릴 수 있다 — 취소·삭제가 전부 전표번호로 대상을 찾는다.
     */
    @Transactional
    public MaterialTransferResponse transfer(MaterialTransferRequest req) {
        if (req.fromWarehouseId().equals(req.toWarehouseId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "출발/도착 창고가 같습니다.");
        }
        Warehouse from = warehouse(req.fromWarehouseId(), "출발 창고");
        Warehouse to = warehouse(req.toWarehouseId(), "도착 창고");

        String transferNo = "TR-" + req.processedDate().format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_TRANSFER);

        List<MaterialTransferResponse.Line> lines = new ArrayList<>();
        for (MaterialTransferRequest.Item item : req.items()) {
            Material material = material(item.materialId());

            int fromQty = inventoryService.applyMaterialDelta(material, from, -item.qty());
            inventoryTxnRepository.save(InventoryTxn.materialTxn(material, from, TxnType.TRANSFER,
                    -item.qty(), req.processedDate(), transferNo, item.memo()));
            int toQty = inventoryService.applyMaterialDelta(material, to, item.qty());
            inventoryTxnRepository.save(InventoryTxn.materialTxn(material, to, TxnType.TRANSFER,
                    item.qty(), req.processedDate(), transferNo, item.memo()));

            lines.add(new MaterialTransferResponse.Line(material.getId(), material.getCode(),
                    material.getName(), item.qty(), fromQty, toQty));
        }
        return new MaterialTransferResponse(transferNo, from.getId(), from.getName(),
                to.getId(), to.getName(), lines);
    }

    /**
     * 자재 폐기. 전표번호(P-)를 붙이고 원장에는 <b>음수</b>로 기록한다(재고를 깎으므로).
     * 화면에는 양수로 보여야 해서 응답에서 부호를 되돌린다 — 도서 폐기와 같은 규율이다.
     */
    @Transactional
    public MaterialDisposalResponse dispose(MaterialDisposalRequest req) {
        Warehouse warehouse = warehouse(req.warehouseId(), "창고");
        String disposalNo = "P-" + req.processedDate().format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_PURGE);

        List<MaterialDisposalResponse.Line> lines = new ArrayList<>();
        for (MaterialDisposalRequest.Item item : req.items()) {
            Material material = material(item.materialId());

            int balance = inventoryService.applyMaterialDelta(material, warehouse, -item.qty());
            inventoryTxnRepository.save(InventoryTxn.materialTxn(material, warehouse, TxnType.DISPOSE,
                    -item.qty(), req.processedDate(), disposalNo, item.memo()));

            lines.add(new MaterialDisposalResponse.Line(material.getId(), material.getCode(),
                    material.getName(), item.qty(), balance));
        }
        return new MaterialDisposalResponse(disposalNo, warehouse.getId(), warehouse.getName(), lines);
    }

    /**
     * 자재 거래 내역(항목 22). 입고·이고·폐기·실사를 최근순으로.
     * 자재·창고·작업구분은 다중선택이다(도서 내역과 같은 규칙).
     */
    @Transactional(readOnly = true)
    public List<MaterialRecordRow> records(LocalDate fromDate, LocalDate toDate,
                                           List<Long> materialIds, List<Long> warehouseIds,
                                           List<TxnType> kinds) {
        return inventoryTxnRepository.findMaterialRecords(fromDate, toDate,
                        MultiSelect.isAny(materialIds), MultiSelect.orPlaceholder(materialIds, 0L),
                        MultiSelect.isAny(warehouseIds), MultiSelect.orPlaceholder(warehouseIds, 0L),
                        MultiSelect.isAny(kinds),
                        MultiSelect.orPlaceholder(kinds, TxnType.INBOUND)).stream()
                .map(t -> new MaterialRecordRow(
                        t.getId(), t.getTradeDate(), kindLabel(t.getTxnType()),
                        t.getWarehouse().getId(), t.getWarehouse().getName(),
                        t.getMaterial().getId(), t.getMaterial().getCode(),
                        t.getMaterial().getName(), t.getMaterial().getMaterialType(),
                        t.getQty(), t.getUnitCost(), t.getRefNo(), t.getMemo()))
                .toList();
    }

    /** 화면 표기. 담당자는 INBOUND 가 아니라 '입고'라고 읽는다. */
    private static String kindLabel(TxnType type) {
        return switch (type) {
            case INBOUND -> "입고";
            case TRANSFER -> "단순이고";
            case DISPOSE -> "폐기";
            case ADJUST -> "실사";
            default -> type.name();
        };
    }

    private Warehouse warehouse(Long id, String label) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        label + "가 없습니다. id=" + id));
    }

    private Material material(Long id) {
        return materialRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "자재가 없습니다. id=" + id));
    }

    /** 자재 재고 현황. 필터는 전부 선택(미지정=전체). */
    @Transactional(readOnly = true)
    public List<MaterialStockRow> stock(Long materialId, Long warehouseId, MaterialType type) {
        List<MaterialStockRow> rows = new ArrayList<>();
        for (Object[] r : inventoryRepository.materialStock(materialId, warehouseId, type)) {
            rows.add(new MaterialStockRow((Long) r[0], (String) r[1], (String) r[2],
                    (MaterialType) r[3], (Long) r[4], (String) r[5], ((Number) r[6]).intValue()));
        }
        return rows;
    }

}
