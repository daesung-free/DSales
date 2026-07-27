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
        Warehouse warehouse = Warehouse.create(req.code(), req.name(), req.type(),
                req.physicalStockOrDefault(), resolveOwner(req.ownerClientId()));
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }

    @Transactional
    public WarehouseResponse update(Long id, WarehouseUpdateRequest req) {
        Warehouse warehouse = getOrThrow(id);
        warehouse.update(req.name(), req.type(), req.physicalStock(), resolveOwner(req.ownerClientId()));
        return WarehouseResponse.from(warehouse);
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
