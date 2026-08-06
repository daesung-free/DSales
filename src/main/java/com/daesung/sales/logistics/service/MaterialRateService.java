package com.daesung.sales.logistics.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.repository.InventoryTxnRepository;
import com.daesung.sales.logistics.dto.AssemblyCostRow;
import com.daesung.sales.logistics.dto.MaterialRateRequest;
import com.daesung.sales.logistics.dto.MaterialRateResponse;
import com.daesung.sales.logistics.entity.MaterialRate;
import com.daesung.sales.logistics.repository.MaterialRateRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 자재 단가 마스터 + 세트 조립 작업비 조회.
 * 근거: 발주처 확정 2026-08-05 — 조립 작업비는 물류비용등록 단가 기준 자동계산,
 * 물류팀은 계산된 내역을 다운로드해 가공 후 정산 요청.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MaterialRateService {

    private final MaterialRateRepository rateRepository;
    private final InventoryTxnRepository inventoryTxnRepository;

    public List<MaterialRateResponse> list() {
        return rateRepository.findAllByOrderByMaterialTypeAscPackTypeAsc().stream()
                .map(MaterialRateResponse::from).toList();
    }

    /** 등록/수정(자재구분×작업구분 단위 upsert). 같은 조합이 두 벌 생기지 않는다. */
    @Transactional
    public MaterialRateResponse upsert(MaterialRateRequest req) {
        int packType = (req.packType() == null) ? MaterialRate.COMMON_PACK_TYPE : req.packType();
        MaterialRate rate = rateRepository.findByMaterialTypeAndPackType(req.materialType(), packType)
                .orElseGet(() -> MaterialRate.of(req.materialType(), packType, req.unitRate(), req.memo()));
        rate.update(req.unitRate(), req.memo());
        return MaterialRateResponse.from(rateRepository.save(rate));
    }

    @Transactional
    public void delete(Long id) {
        MaterialRate rate = rateRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "자재 단가가 없습니다. id=" + id));
        rateRepository.delete(rate);
    }

    /** 기간별 세트 조립 작업비 내역. */
    public List<AssemblyCostRow> assemblyCosts(LocalDate from, LocalDate to, Long warehouseId) {
        List<Object[]> raw = inventoryTxnRepository.assemblyCosts(from, to, warehouseId);
        List<AssemblyCostRow> rows = new ArrayList<>(raw.size());
        for (Object[] r : raw) {
            rows.add(new AssemblyCostRow(
                    ((java.sql.Date) r[0]).toLocalDate(), (String) r[1], (String) r[2], (String) r[3],
                    ((Number) r[4]).longValue(), ((Number) r[5]).longValue(), (String) r[6]));
        }
        return rows;
    }
}
