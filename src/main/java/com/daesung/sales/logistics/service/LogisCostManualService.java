package com.daesung.sales.logistics.service;

import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.logistics.dto.LogisCostManualRequest;
import com.daesung.sales.logistics.dto.LogisCostManualResponse;
import com.daesung.sales.logistics.entity.LogisCostManual;
import com.daesung.sales.logistics.repository.LogisCostManualRepository;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 수기 물류작업비 관리(28p 에디팅 모드). 근거: 발주처 회신 2026-08-21.
 *
 * <p><b>자동계산 로직은 건드리지 않는다</b>(발주처 요구). 수기 행을 따로 두고
 * 조회 시 합칠 뿐이라, 자동계산분이 어떻게 바뀌든 이쪽은 영향이 없다.
 *
 * <p>★마감월은 손대지 못한다. 등록·수정·삭제·복사 모두 월마감을 검사한다 —
 * 막지 않으면 과거 작업비 고정 원칙(V49 스냅샷)이 수기 행으로 뚫린다.
 * 스냅샷은 자동계산분만 굳히고 수기 행은 우리 DB에 그대로 있으므로,
 * <b>수기 행의 불변성은 이 잠금이 보장한다</b>.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LogisCostManualService {

    private final LogisCostManualRepository manualRepository;
    private final PeriodLockService periodLockService;
    private final CurrentAuditor currentAuditor;

    public List<LogisCostManualResponse> search(LocalDate from, LocalDate to) {
        return manualRepository.findByReqDateBetween(from, to).stream()
                .map(LogisCostManualResponse::from).toList();
    }

    @Transactional
    public LogisCostManualResponse create(LogisCostManualRequest req) {
        periodLockService.assertNotLocked(req.reqDate());
        LogisCostManual m = LogisCostManual.create(req.reqDate(), req.reqCd(),
                req.productCode(), req.productName(), req.grade(), req.dtlCd(), req.detailName(),
                req.partnerCode(), req.partnerName(),
                req.materialQty(), req.paperQty(), req.paperAmount(),
                req.omrQty(), req.omrAmount(), req.etcQty(), req.etcAmount(),
                req.inwon(), req.basicAmount(), req.tradeAmount(), req.applyGn(), req.memo());
        return LogisCostManualResponse.from(manualRepository.save(m));
    }

    /**
     * 수정. 접수일자·신청번호·시행코드는 바꾸지 않는다(귀속 축).
     * <b>원래 달과 옮겨 갈 달을 둘 다 볼 필요가 없다</b> — 날짜를 못 바꾸므로 달이 옮겨지지 않는다.
     */
    @Transactional
    public LogisCostManualResponse update(Long id, LogisCostManualRequest req) {
        LogisCostManual m = getOrThrow(id);
        periodLockService.assertNotLocked(m.getReqDate());
        m.apply(req.productCode(), req.productName(), req.grade(), req.detailName(),
                req.partnerCode(), req.partnerName(),
                req.materialQty(), req.paperQty(), req.paperAmount(),
                req.omrQty(), req.omrAmount(), req.etcQty(), req.etcAmount(),
                req.inwon(), req.basicAmount(), req.tradeAmount(), req.applyGn(), req.memo());
        return LogisCostManualResponse.from(m);
    }

    /** 복사(28p '복사'). 같은 내용으로 새 행을 만든다 — 비슷한 건을 반복 입력하는 화면이다. */
    @Transactional
    public LogisCostManualResponse copy(Long id) {
        LogisCostManual src = getOrThrow(id);
        periodLockService.assertNotLocked(src.getReqDate());
        return LogisCostManualResponse.from(manualRepository.save(src.copy()));
    }

    /** 삭제(논리삭제) — 지운 금액이 왜 사라졌는지 남아야 한다. */
    @Transactional
    public void delete(Long id) {
        LogisCostManual m = getOrThrow(id);
        periodLockService.assertNotLocked(m.getReqDate());
        m.markDeleted(currentAuditor.username());
    }

    private LogisCostManual getOrThrow(Long id) {
        return manualRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "수기 물류작업비가 없습니다. id=" + id));
    }
}
