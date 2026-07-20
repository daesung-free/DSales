package com.daesung.sales.warehouse.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.warehouse.dto.WarehouseCreateRequest;
import com.daesung.sales.warehouse.dto.WarehouseResponse;
import com.daesung.sales.warehouse.entity.Warehouse;
import com.daesung.sales.warehouse.repository.WarehouseRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WarehouseService {

    private final WarehouseRepository warehouseRepository;

    public PageResponse<WarehouseResponse> findAll(Pageable pageable) {
        return PageResponse.of(warehouseRepository.findAll(pageable).map(WarehouseResponse::from));
    }

    public WarehouseResponse findById(Long id) {
        Warehouse warehouse = warehouseRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "창고가 없습니다. id=" + id));
        return WarehouseResponse.from(warehouse);
    }

    @Transactional
    public WarehouseResponse create(WarehouseCreateRequest req) {
        warehouseRepository.findByCode(req.code()).ifPresent(w -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 창고코드: " + req.code());
        });
        Warehouse warehouse = Warehouse.create(req.code(), req.name(), req.type());
        return WarehouseResponse.from(warehouseRepository.save(warehouse));
    }
}
