package com.daesung.sales.closing.service;

import com.daesung.sales.closing.repository.PeriodLockRepository;
import com.daesung.sales.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Component;

/**
 * 월마감 상태 캐시. 모든 재무 쓰기(매출/취소/위탁정산/수금)마다 조회되는 hot-path → Redis 캐시.
 * 잠금/해제 시 evict로 즉시 갱신. (별 컴포넌트로 분리해 self-invocation 캐시 미적용 문제 회피)
 */
@Component
@RequiredArgsConstructor
public class PeriodLockCache {

    private final PeriodLockRepository periodLockRepository;

    @Cacheable(cacheNames = CacheConfig.PERIOD_LOCK, key = "#year + '-' + #month")
    public boolean isLocked(int year, int month) {
        return periodLockRepository.isLocked(year, month);
    }

    @CacheEvict(cacheNames = CacheConfig.PERIOD_LOCK, key = "#year + '-' + #month")
    public void evict(int year, int month) {
        // 잠금/해제 후 호출 → 다음 조회는 DB 재조회.
    }
}
