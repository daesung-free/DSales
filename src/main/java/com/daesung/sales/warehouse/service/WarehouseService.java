package com.daesung.sales.warehouse.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.warehouse.dto.WarehouseCreateRequest;
import com.daesung.sales.warehouse.dto.WarehouseResponse;
import com.daesung.sales.warehouse.dto.WarehouseUpdateRequest;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.entity.WarehouseType;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;
    private final PartnerRepository partnerRepository;
    private final com.daesung.sales.inventory.repository.InventoryRepository inventoryRepository;

    public PageResponse<WarehouseResponse> findAll(String keyword, Pageable pageable) {
        Page<Warehouse> page = (keyword == null || keyword.isBlank())
                ? warehouseRepository.findAll(pageable)
                : warehouseRepository.findByCodeContainingIgnoreCaseOrNameContainingIgnoreCase(
                        keyword, keyword, pageable);
        return PageResponse.of(page.map(WarehouseResponse::from));
    }

    public WarehouseResponse findById(Long id) {
        return WarehouseResponse.from(getOrThrow(id));
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest req) {
        warehouseRepository.findByCode(req.code()).ifPresent(w -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 창고코드: " + req.code());
        });
        Partner owner = ownerFor(req.type(), req.ownerClientId(), null);
        Warehouse warehouse = Warehouse.create(req.code(), req.name(), req.type(),
                req.physicalStockOrDefault(), owner);
        warehouse.applyExtra(req.useYn(), req.memo());
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest req) {
        Warehouse warehouse = getOrThrow(id);
        Partner owner = ownerFor(req.type(), req.ownerClientId(), warehouse.getOwnerClient());
        warehouse.update(req.name(), req.type(), req.physicalStock(), owner);
        warehouse.applyExtra(req.useYn(), req.memo());
        return WarehouseResponse.from(warehouse);
    }

    /**
     * 창고유형에 맞는 소속거래처를 정한다.
     *
     * <p>★<b>위탁창고는 소속 거래처가 반드시 있어야 한다.</b> 위탁 미결정산이
     * {@code 창고 ↔ 거래처} 매핑으로 도는 구조라, 소속이 없는 위탁창고에 재고가 들어가면
     * <b>그 미결을 누구 것으로 정산할지 특정할 수 없다.</b>
     * ‼️예전엔 이 검증이 화면에만 있었다 — API를 직접 부르면 소속 없는 위탁창고가
     * 그대로 만들어졌다(2026-09-11 점검에서 발견, D-12).
     *
     * <p>★<b>수정 때 안 보낸 소속은 지우지 않는다.</b> {@code Warehouse.update}는 받은 값을
     * 그대로 덮어쓰므로, 이름만 고치려고 {@code ownerClientId}를 빼면 소속이 조용히 날아갔다.
     * 도서 수정에서 같은 문제를 부분 수정으로 고친 적이 있다(7d8361f) — 여기도 같은 규칙이다.
     *
     * <p>물류창고는 소속이 없는 것이 정상이라 무엇이 오든 {@code null}로 만든다.
     *
     * @param current 수정 전 소속(신규 등록이면 {@code null})
     */
    /**
     * 창고 사용 중지(논리삭제) — {@code useYn=false}.
     *
     * <p>★행을 지우지 않는다. 과거 재고 이벤트({@code inventory_txn})가 창고를 가리키고 있어
     * 지우면 수불부가 "어느 창고였는지 모르는" 행을 갖게 된다.
     * 미사용 창고는 목록·선택지에서 빠진다.
     *
     * <p>‼️재고가 남아 있으면 막는다 — 물건이 있는 창고를 목록에서 치우면
     * 그 재고를 아무도 못 찾는다. 먼저 이고로 비워야 한다.
     */
    @Transactional
    public void discontinue(Long id) {
        Warehouse warehouse = getOrThrow(id);
        int balance = inventoryRepository.totalQtyByWarehouse(id);
        if (balance != 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "재고가 남아 있어 중지할 수 없습니다. 현재 " + balance + "부 — 먼저 다른 창고로 옮기세요.");
        }
        warehouse.applyExtra(false, null);
    }

    private Partner ownerFor(WarehouseType type, Long ownerClientId, Partner current) {
        if (type != WarehouseType.CONSIGN) {
            return null;   // 물류창고는 소속 없음
        }
        Partner owner = (ownerClientId != null) ? resolveOwner(ownerClientId) : current;
        if (owner == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "위탁창고는 소속 거래처를 지정해야 합니다.");
        }
        return owner;
    }

    /** 소속거래처 id → Partner(없으면 예외). null이면 소속 없음. */
    private Partner resolveOwner(Long ownerClientId) {
        if (ownerClientId == null) {
            return null;
        }
        return partnerRepository.findById(ownerClientId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "소속거래처가 없습니다. id=" + ownerClientId));
    }

    private Warehouse getOrThrow(Long id) {
        return warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "창고가 없습니다. id=" + id));
    }
}
