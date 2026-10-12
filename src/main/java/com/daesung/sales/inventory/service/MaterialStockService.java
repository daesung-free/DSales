package com.daesung.sales.inventory.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.common.query.MultiSelect;
import com.daesung.sales.inventory.dto.MaterialInboundRequest;
import com.daesung.sales.inventory.dto.MaterialInboundResponse;
import com.daesung.sales.inventory.dto.MaterialIoRequest;
import com.daesung.sales.inventory.dto.MaterialIoResponse;
import com.daesung.sales.inventory.dto.MaterialRecordRow;
import com.daesung.sales.inventory.dto.MaterialStockRow;
import com.daesung.sales.inventory.dto.MaterialTransferRequest;
import com.daesung.sales.inventory.dto.MaterialTransferResponse;
import com.daesung.sales.inventory.entity.MaterialIo;
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
    private final com.daesung.sales.product.repository.ProductRepository productRepository;
    private final com.daesung.sales.material.repository.MaterialBomRepository materialBomRepository;
    private final com.daesung.sales.audit.service.StatusHistoryService statusHistoryService;

    /** 자재 입고. 품목마다 (1) 재고이벤트 INBOUND 기록 + (2) 잔량 가산을 한 트랜잭션으로. */
    @Transactional
    public MaterialInboundResponse inbound(MaterialInboundRequest req) {
        Warehouse warehouse = warehouse(req.destinationWarehouseId(), "창고");
        Partner supplier = partnerRepository.findById(req.supplierClientId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.supplierClientId()));

        // ★도서 단위 입고(발주처 #2 "도서 선택 → 하위 자재 수량 입력"). 도서를 주면 검사한다.
        java.util.Set<Long> allowed = assertProductAllows(req);

        // 전표번호는 도서 입고와 같은 채번(IN-)을 쓴다. 되돌릴 때 "무슨 전표냐"를 한 곳에서 찾는다.
        String inboundNo = "IN-" + req.processedDate().format(YYYYMMDD) + "-"
                + sequenceService.next(SequenceService.SEQ_INBOUND);

        List<MaterialInboundResponse.Line> lines = new ArrayList<>();
        for (MaterialInboundRequest.Item item : req.items()) {
            Material material = material(item.materialId());
            if (allowed != null && !allowed.contains(material.getId())) {
                throw new BusinessException(ErrorCode.INVALID_INPUT,
                        "그 도서의 구성 자재가 아닙니다: " + material.getCode()
                                + ". 도서를 바꿨다면 자재 목록을 다시 불러오세요.");
            }

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
     * 도서 단위 입고일 때의 사전 검사(발주처 1차회신 2026-10-02 #2).
     *
     * <p>★<b>DSRE 병행 상품은 막는다.</b> 회신 원문 — "DSRE 병행 상품은 조회만
     * (DSRE 병행 상품의 자재 입출고는 DSRE에서 그대로 관리) / 매출프로그램 단독 상품은
     * 이 프로그램에서 자재 단위로 입고 등록". 판정 기준은 #1 이 정한 <b>단가노출</b>이다
     * (단독관리=Y / 병행=N).
     *
     * <p>‼️여기서 안 막으면 <b>같은 자재 재고가 우리와 DSRE 두 곳에서 따로 움직인다</b> —
     * 둘 다 맞아 보이지만 실물과는 둘 다 다르다. 이 프로젝트가 레거시에서 겪은 결함 그대로다.
     *
     * @return 허용 자재 id 집합. 도서를 안 줬으면 null(검사 없음)
     */
    private java.util.Set<Long> assertProductAllows(MaterialInboundRequest req) {
        if (req.productId() == null) {
            return null;
        }
        var product = productRepository.findById(req.productId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "도서가 없습니다. id=" + req.productId()));
        if (!product.isPriceVisible()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "DSRE 병행 상품의 자재는 여기서 입고할 수 없습니다: " + product.getCode()
                            + ". 병행 상품의 자재 입출고는 DSRE에서 관리합니다(발주처 확정 2026-10-02). "
                            + "단독관리로 바꾸려면 도서관리에서 단가노출을 켜세요.");
        }
        java.util.Set<Long> ids = new java.util.HashSet<>();
        materialBomRepository.findBySet(product.getId())
                .forEach(b -> ids.add(b.getMaterial().getId()));
        if (ids.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "그 도서에 등록된 구성 자재가 없습니다: " + product.getCode()
                            + ". 도서관리 > 자재관리에서 먼저 구성을 등록하세요.");
        }
        return ids;
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
     * 자재 입출고 등록(입고 외 5종) — 출고·회수2종·폐기·파손.
     *
     * <p>근거: 9/27 회의 항목 22 "dsre 자재입출고관리 참고". DSRE({@code FM_LOGI_MatInOut.cs})가
     * 한 화면에서 구분을 골라 처리하는 구조라 그대로 맞췄다.
     *
     * <p>★<b>부호는 여기서 붙인다.</b> 화면은 언제나 양수를 보낸다 —
     * "120장 버림"을 담당자가 −120으로 입력하게 하지 않는다.
     *
     * <p>전표번호는 폐기 계열이면 {@code P-}, 들어오는 계열이면 {@code IN-} 을 쓴다.
     * 출고는 매출 전표(I-)와 섞이면 안 되므로 폐기와 같은 {@code P-} 계열을 쓴다 —
     * 자재 출고는 매출이 아니라 소모다.
     */
    @Transactional
    public MaterialIoResponse io(MaterialIoRequest req) {
        if (req.io() == MaterialIo.INBOUND) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "입고는 이 API 로 등록하지 않습니다. 거래처·입고단가가 필요해 "
                            + "POST /stock/materials/inbound 를 쓰세요.");
        }
        Warehouse warehouse = warehouse(req.warehouseId(), "창고");

        String prefix = req.io().isOutgoing() ? "P-" : "IN-";
        String seq = req.io().isOutgoing() ? SequenceService.SEQ_PURGE : SequenceService.SEQ_INBOUND;
        String refNo = prefix + req.processedDate().format(YYYYMMDD) + "-" + sequenceService.next(seq);

        List<MaterialIoResponse.Line> lines = new ArrayList<>();
        for (MaterialIoRequest.Item item : req.items()) {
            Material material = material(item.materialId());
            int delta = req.io().sign() * item.qty();

            int balance = inventoryService.applyMaterialDelta(material, warehouse, delta);
            inventoryTxnRepository.save(InventoryTxn.materialTxn(material, warehouse,
                    req.io().txnType(), req.io(), delta, req.processedDate(), refNo, item.memo()));

            lines.add(new MaterialIoResponse.Line(material.getId(), material.getCode(),
                    material.getName(), item.qty(), balance));
        }
        return new MaterialIoResponse(refNo, req.io().name(), req.io().label(),
                warehouse.getId(), warehouse.getName(), lines);
    }

    /**
     * 자재 입출고 전표 <b>수정</b>(프론트 10/06 요청 2-3 · 재문의 2026-10-12 C-5).
     *
     * <p>★<b>삭제 + 재등록이다</b>(한 트랜잭션). 새 전표번호가 나간다 —
     * <b>폐기 수정과 같은 방식</b>이고, 이유도 같다: 자재 내역 화면이 재고 이벤트를 직접 읽어서
     * 제자리에서 고치면 한 전표가 두 줄로 보이거나 상세와 합계가 어긋난다.
     * 취소+재등록도 안 된다 — 역분개가 취소한 달로 잡혀 원 수량이 기간 합계에 남는다.
     *
     * <p>‼️<b>입고(IN-) 전표는 여기서 못 고친다.</b> 입고는 거래처·입고단가가 붙어 있어
     * 축이 다르다({@code POST /stock/materials/inbound}). 섞으면 그 값들이 조용히 사라진다.
     */
    @Transactional
    public MaterialIoResponse reviseIo(String refNo, MaterialIoRequest req, String actor) {
        List<InventoryTxn> origins = inventoryTxnRepository.findAllByRefNo(refNo);
        if (origins.isEmpty()) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "전표가 없습니다: " + refNo);
        }
        if (origins.stream().noneMatch(t -> t.getMaterial() != null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "자재 전표가 아닙니다: " + refNo + ". 도서 전표는 해당 화면에서 고치세요.");
        }
        if (origins.stream().anyMatch(t -> t.getTxnType() == TxnType.INBOUND)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "자재 **입고** 전표는 이 API 로 고칠 수 없습니다: " + refNo
                            + ". 거래처·입고단가가 함께 있어 입고 등록 경로로 다시 올려야 합니다.");
        }

        inventoryService.deleteVoucher(refNo, "자재 입출고 수정으로 재등록", actor);
        MaterialIoResponse revised = io(req);

        // ★어느 전표가 어느 전표로 바뀌었는지 남긴다 — 삭제 이력과 등록만 남으면
        //   나중에 둘이 같은 건이었다는 사실을 아무도 못 잇는다(폐기 수정과 같은 규칙).
        statusHistoryService.record(
                com.daesung.sales.audit.entity.StatusEntityType.INVENTORY_VOUCHER,
                origins.get(0).getId(), "revised", refNo, revised.refNo(),
                "자재 입출고 수정: " + refNo + " → " + revised.refNo());
        return revised;
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
                        t.getId(), t.getTradeDate(), ioLabel(t),
                        t.getWarehouse().getId(), t.getWarehouse().getName(),
                        t.getMaterial().getId(), t.getMaterial().getCode(),
                        t.getMaterial().getName(), t.getMaterial().getMaterialType(),
                        t.getMaterialIo(), t.getQty(), t.getUnitCost(), t.getRefNo(), t.getMemo()))
                .toList();
    }

    /**
     * 화면 표기. 담당자는 OUTBOUND 가 아니라 '출고'라고 읽는다.
     *
     * <p>입출고 구분이 있으면 그 표기를 쓴다 — 폐기와 파손, 회수 2종이 여기서 갈린다.
     * 이고는 우리 축이라 구분이 없다(DSRE 에 창고 개념이 없어 대응 구분도 없다).
     */
    private static String ioLabel(InventoryTxn t) {
        if (t.getMaterialIo() != null) {
            return t.getMaterialIo().label();
        }
        return switch (t.getTxnType()) {
            case TRANSFER -> "단순이고";
            case ADJUST -> "실사";
            default -> t.getTxnType().name();
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
