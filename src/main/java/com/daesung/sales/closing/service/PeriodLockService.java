package com.daesung.sales.closing.service;

import com.daesung.sales.closing.dto.PeriodLockResponse;
import com.daesung.sales.closing.entity.PeriodLock;
import com.daesung.sales.closing.repository.PeriodLockRepository;
import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.permission.entity.UserPermissionFlag;
import com.daesung.sales.permission.service.PermissionService;
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
    private final PermissionService permissionService;
    private final org.springframework.beans.factory.ObjectProvider<
            com.daesung.sales.logistics.service.LogisCostDetailService> logisCostDetailService;
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

    /**
     * 월마감(잠금). 없으면 생성 후 잠금. 캐시 무효화. 상태변경 이력 기록.
     *
     * <p>★<b>물류작업비를 함께 굳힌다</b>(발주처 회신 2026-08-21 "과거 데이터 고정").
     * 물류비는 조회할 때마다 다시 계산되므로, 굳혀 두지 않으면 나중에 단가를 고쳤을 때
     * 이미 청구가 끝난 달의 금액까지 바뀐다.
     *
     * <p>⚠️DSRE 연동이 꺼진 개발·테스트 환경에서는 굳히지 못하고 넘어간다.
     * 운영에서는 연동이 켜져 있는 것이 전제라 이 경우가 생기지 않는다 —
     * "굳히지 못한 마감"이라는 상태를 업무 규칙으로 만들지 않는다.
     */
    @Transactional
    public PeriodLockResponse lock(int year, int month, String memo) {
        assertFlag(UserPermissionFlag.PERIOD_LOCK, "마감 확정");
        freezeLogisCost(year, month);
        PeriodLock pl = getOrCreate(year, month);
        boolean before = pl.isLocked();   // ★바꾸기 전에 읽는다
        pl.lock(currentAuditor.username(), memo);
        periodLockCache.evict(year, month);
        // 같은 월을 여러 번 여닫아도 각 회차가 남는다(기존엔 마지막 상태만 남아 중간 기록이 소실됐다).
        statusHistoryService.record(StatusEntityType.PERIOD_LOCK, pl.getId(), "locked",
                before, true, memo);
        return PeriodLockResponse.from(pl);
    }

    /**
     * 월마감 해제(재오픈). 캐시 무효화. 상태변경 이력 기록 — "왜 풀었나"가 감사 핵심 질문이다.
     *
     * <p>굳혀 둔 물류작업비도 함께 지운다. 다시 마감하면 <b>그 시점 단가로 새로 굳는다</b> —
     * 풀어 놓고 고친 뒤 다시 닫는 것이 재계산의 유일한 경로다.
     */
    @Transactional
    public PeriodLockResponse unlock(int year, int month, String reason) {
        assertFlag(UserPermissionFlag.PERIOD_UNLOCK, "마감 해제");
        logisCostDetailService.ifAvailable(s -> s.unfreeze(year, month));
        PeriodLock pl = getOrCreate(year, month);
        boolean before = pl.isLocked();
        pl.unlock();
        periodLockCache.evict(year, month);
        statusHistoryService.record(StatusEntityType.PERIOD_LOCK, pl.getId(), "locked",
                before, false, reason);
        return PeriodLockResponse.from(pl);
    }

    /**
     * 사용자 개별 권한(3단계) 검사. 근거: 발주처 「사용자권한_구조 설계 예시」 3단계 —
     * "④·⑤ 항목처럼 특정 담당자에게만 부여하는 권한은, 역할이 아니라
     * 사용자 ID 단위 Y/N 필드로 관리자가 직접 지정."
     *
     * <p>마감 확정과 해제를 <b>따로</b> 둔다 — 해제는 이미 닫은 장부를 다시 여는 것이라
     * 확정보다 민감하다. 같은 권한으로 묶으면 확정만 맡기고 싶은 담당자에게
     * 해제 권한까지 딸려 간다.
     */
    private void assertFlag(String flagKey, String what) {
        if (!permissionService.hasFlag(currentAuditor.username(), flagKey)) {
            throw new BusinessException(ErrorCode.FORBIDDEN,
                    what + " 권한이 없습니다. 관리자에게 요청하세요.");
        }
    }

    /**
     * 마감 시 물류작업비를 굳힌다. 서비스가 {@code @ConditionalOnProperty}라
     * DSRE 연동이 꺼지면 빈이 없고, 그때는 굳히지 않고 넘어간다.
     *
     * <p>연동이 꺼진 개발·테스트 환경에서만 생기는 경우다. 운영에서는 켜져 있는 것이 전제다.
     */
    private void freezeLogisCost(int year, int month) {
        logisCostDetailService.ifAvailable(svc -> svc.freeze(year, month));
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
