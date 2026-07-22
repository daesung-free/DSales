package com.daesung.sales.salestype.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.config.CacheConfig;
import com.daesung.sales.salestype.entity.ShipmentType;
import com.daesung.sales.salestype.repository.OutTypeMappingRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * 출고유형→회계구분 룩업(캐시). 매출 라인마다 조회되는 hot-path + 시드값(불변) → Redis 캐시.
 * 매핑 변경 시 evictAll로 무효화(현재 매핑 편집 API 없음 — 대비용).
 */
@Service
@RequiredArgsConstructor
public class OutTypeLookupService {

    private final OutTypeMappingRepository outTypeMappingRepository;

    /**
     * 회계구분 이름 조회(캐시). enum은 Redis JSON에서 타입이 유실되므로 이름(String)으로 캐시.
     * 호출자는 SalesCategory.valueOf로 변환(같은 빈 내 self-invocation 시 캐시 미적용이므로 직접 호출할 것).
     */
    @Cacheable(cacheNames = CacheConfig.OUT_TYPE_MAPPING, key = "#shipmentType")
    public String salesCategoryNameOf(ShipmentType shipmentType) {
        return outTypeMappingRepository.findById(shipmentType)
                .map(m -> m.getSalesCategory().name())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_INPUT,
                        "출고유형 매핑이 없습니다: " + shipmentType));
    }

    @CacheEvict(cacheNames = CacheConfig.OUT_TYPE_MAPPING, allEntries = true)
    public void evictAll() {
        // 매핑 마스터 변경 시 호출(무효화).
    }
}
