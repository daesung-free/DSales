package com.daesung.sales.closing.service;

import com.daesung.sales.closing.dto.PeriodLockResponse;
import com.daesung.sales.closing.entity.PeriodLock;
import com.daesung.sales.closing.repository.PeriodLockRepository;
import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.audit.CurrentAuditor;
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
    private final StatusHistoryService statusHistoryService;
    private final CurrentAuditor currentAuditor;
    private final PeriodLockCache periodLockCache;

    /** 해당 일자가 속한 월이 마감이면 PERIOD_LOCKED. 모든 재무 쓰기 API의 횡단 검사(캐시 조회). */
    @Transactional(readOnly = true)
    public void assertNotLocked(LocalDate date) {
        if (periodLockCache.isLocked(date.getYear(), date.getMonthValue())) {
            throw new BusinessException(ErrorCode.PERIOD_LOCKED,
                    date.getYear() + "년 " + date.getMonthValue() + "월은 마감되어 재무 등록/수정이 불가합니다.");
        }
    }

    /** 월마감(잠금). 없으면 생성 후 잠금. 캐시 무효화. 상태변경 이력 기록. */
    @Transactional
    public PeriodLockResponse lock(int year, int month, String memo) {
        PeriodLock pl = getOrCreate(year, month);
        boolean before = pl.isLocked();   // ★바꾸기 전에 읽는다
        pl.lock(currentAuditor.username(), memo);
        periodLockCache.evict(year, month);
        // 같은 월을 여러 번 여닫아도 각 회차가 남는다(기존엔 마지막 상태만 남아 중간 기록이 소실됐다).
        statusHistoryService.record(StatusEntityType.PERIOD_LOCK, pl.getId(), "locked",
                before, true, memo);
        return PeriodLockResponse.from(pl);
    }

    /** 월마감 해제(재오픈). 캐시 무효화. 상태변경 이력 기록 — "왜 풀었나"가 감사 핵심 질문이다. */
    @Transactional
    public PeriodLockResponse unlock(int year, int month, String reason) {
        PeriodLock pl = getOrCreate(year, month);
        boolean before = pl.isLocked();
        pl.unlock();
        periodLockCache.evict(year, month);
        statusHistoryService.record(StatusEntityType.PERIOD_LOCK, pl.getId(), "locked",
                before, false, reason);
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
