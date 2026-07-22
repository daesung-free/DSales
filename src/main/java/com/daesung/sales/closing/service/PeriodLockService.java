package com.daesung.sales.closing.service;

import com.daesung.sales.closing.dto.PeriodLockResponse;
import com.daesung.sales.closing.entity.PeriodLock;
import com.daesung.sales.closing.repository.PeriodLockRepository;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 월마감(period_locks). 근거: 요구사항정의서 3대 로직/DB-30.
 * 마감된 월의 재무 쓰기(매출 등록/취소·위탁정산·수금)를 assertNotLocked로 차단(PERIOD_LOCKED).
 */
@Service
@RequiredArgsConstructor
public class PeriodLockService {

    private final PeriodLockRepository periodLockRepository;
    private final PeriodLockCache periodLockCache;

    /** 해당 일자가 속한 월이 마감이면 PERIOD_LOCKED. 모든 재무 쓰기 API의 횡단 검사(캐시 조회). */
    @Transactional(readOnly = true)
    public void assertNotLocked(LocalDate date) {
        if (periodLockCache.isLocked(date.getYear(), date.getMonthValue())) {
            throw new BusinessException(ErrorCode.PERIOD_LOCKED,
                    date.getYear() + "년 " + date.getMonthValue() + "월은 마감되어 재무 등록/수정이 불가합니다.");
        }
    }

    /** 월마감(잠금). 없으면 생성 후 잠금. 캐시 무효화. */
    @Transactional
    public PeriodLockResponse lock(int year, int month, String memo) {
        PeriodLock pl = getOrCreate(year, month);
        pl.lock(null, memo); // lockedBy는 RBAC 도입 시 인증 주체로
        periodLockCache.evict(year, month);
        return PeriodLockResponse.from(pl);
    }

    /** 월마감 해제(재오픈). 캐시 무효화. */
    @Transactional
    public PeriodLockResponse unlock(int year, int month) {
        PeriodLock pl = getOrCreate(year, month);
        pl.unlock();
        periodLockCache.evict(year, month);
        return PeriodLockResponse.from(pl);
    }

    /** 연도별 마감 현황. */
    @Transactional(readOnly = true)
    public List<PeriodLockResponse> list(int year) {
        return periodLockRepository.findByPeriodYearOrderByPeriodMonth(year).stream()
                .map(PeriodLockResponse::from)
                .toList();
    }

    private PeriodLock getOrCreate(int year, int month) {
        return periodLockRepository.findByPeriodYearAndPeriodMonth(year, month)
                .orElseGet(() -> periodLockRepository.save(PeriodLock.create(year, month)));
    }
}
