package com.daesung.sales.logistics.service;

import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.LogisCostRate;
import com.daesung.sales.logistics.dto.WorkTypeApplyResult;
import com.daesung.sales.logistics.dto.WorkTypeRequest;
import com.daesung.sales.logistics.dto.WorkTypeResponse;
import com.daesung.sales.logistics.entity.LogisRateOverride;
import com.daesung.sales.logistics.entity.WorkType;
import com.daesung.sales.logistics.repository.LogisRateOverrideRepository;
import com.daesung.sales.logistics.repository.WorkTypeRepository;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 작업구분 관리(36p 물류비용등록). 근거: 발주처 회신 2026-08-21 + 첨부 수정요청안 ①②.
 *
 * <p>작업구분은 <b>관리형 값</b>이다(신규등록·수정·삭제 가능). 이름과 기준단가는 우리 DB에 있고,
 * 단가 값은 지금처럼 DSRE2 {@code tbl_logis_cost}에 써넣는다 —
 * DSRE2엔 {@code PACKTYPE} 숫자만 있고 이름을 적을 자리가 없기 때문이다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkTypeService {

    private final WorkTypeRepository workTypeRepository;
    private final LogisRateOverrideRepository overrideRepository;
    /**
     * ‼️DSRE 연동이 꺼진 환경에서는 빈이 없다({@code daesung.dsre.enabled=false}).
     * <b>작업구분 마스터 관리는 DSRE 없이도 되어야 하므로</b> 선택 의존으로 받는다 —
     * 필수로 두면 연동이 꺼진 것만으로 화면 전체가 뜨지 않는다.
     * DSRE가 필요한 것은 일괄 반영과 사용중 판정뿐이다.
     */
    private final org.springframework.beans.factory.ObjectProvider<DsreGateway> dsreGatewayProvider;
    private final CurrentAuditor currentAuditor;
    private final ProductLogisRateService productLogisRateService;

    public List<WorkTypeResponse> findAll(boolean includeUnused) {
        return workTypeRepository.findAllByOrderBySortOrderAscPackTypeAsc().stream()
                .filter(w -> includeUnused || w.isUseYn())
                .map(WorkTypeResponse::from).toList();
    }

    @Transactional
    public WorkTypeResponse create(WorkTypeRequest req) {
        if (req.packType() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "PACKTYPE 값이 필요합니다.");
        }
        if (workTypeRepository.existsByPackType(req.packType())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 쓰이는 PACKTYPE입니다: " + req.packType());
        }
        WorkType w = WorkType.create(req.packType(), req.name(), req.paper(), req.omr(), req.etc(),
                req.label(), req.basic(), req.trade(), req.useYn(), req.sortOrder());
        return WorkTypeResponse.from(workTypeRepository.save(w));
    }

    /** 수정. packType은 바꾸지 않는다 — 단가 행과 잇는 유일한 키다. */
    @Transactional
    public WorkTypeResponse update(Long id, WorkTypeRequest req) {
        WorkType w = getOrThrow(id);
        w.update(req.name(), req.paper(), req.omr(), req.etc(), req.label(), req.basic(), req.trade(),
                req.useYn(), req.sortOrder());
        return WorkTypeResponse.from(w);
    }

    /**
     * 삭제. <b>그 작업구분을 쓰는 단가 행이 있으면 지우지 않고 비활성으로 돌린다.</b>
     * 지워버리면 그 행들의 PACKTYPE 숫자가 무슨 뜻인지 알 수 없게 된다(DSRE2엔 이름이 없다).
     *
     * @return true=실제 삭제, false=사용 중이라 비활성 처리
     */
    @Transactional
    public boolean delete(Long id) {
        WorkType w = getOrThrow(id);
        DsreGateway gateway = dsreGatewayProvider.getIfAvailable();
        // 연동이 꺼져 있으면 사용 여부를 알 수 없다 → 지우지 않고 비활성으로 둔다(안전한 쪽).
        boolean inUse = (gateway == null)
                || gateway.listLogisCosts().stream().anyMatch(r -> r.packtype() == w.getPackType());
        if (inUse) {
            w.deactivate();
            return false;
        }
        workTypeRepository.delete(w);
        return true;
    }

    /**
     * 기준단가를 그 작업구분이 붙은 <b>단가 행 전체에 일괄 반영</b>한다(36p ①).
     *
     * <p><b>예외로 등록된 행은 건너뛴다.</b> 담당자가 개별 수정한 행은 의도적으로 다른 값이라,
     * 일괄적용 한 번에 조용히 덮이면 그 상품이 잘못된 단가로 청구된다.
     *
     * <p>⚠️마감 확정된 월의 물류작업비가 소급 변경되지 않게 하는 것은 <b>이 단계가 아니다</b> —
     * 단가는 월별이 아니라 전역이라 여기서 막을 수 없다. 마감 시점에 계산 결과를 굳혀 두는 것이
     * 그 보장을 준다(별도 작업).
     */
    @Transactional
    public WorkTypeApplyResult apply(Long id) {
        return run(id, false);
    }

    /**
     * 일괄 반영 <b>미리보기</b> — 무엇도 바꾸지 않고 "이대로 누르면 몇 건이 덮이는지"만 돌려준다.
     * 근거: 프론트 회신(2026-09-17) B-13 — "반영 전에 대상 건수를 미리 보여 드릴 수 없습니다".
     *
     * <p>★미리보기가 없으면 담당자는 <b>눌러 봐야</b> 몇 건인지 안다. 그런데 이 동작은
     * 되돌리는 경로가 없다(덮이기 전 값이 어디에도 안 남는다). 확인 없이 누르게 두면
     * 잘못 고른 작업구분 하나로 수백 행의 단가가 바뀌고 되돌릴 방법이 없다.
     *
     * <p>반영과 <b>같은 코드</b>로 센다 — 따로 세면 미리보기엔 12건인데 실제로는 15건이
     * 바뀌는 상황이 생기고, 그러면 미리보기가 있으나 마나다.
     */
    public WorkTypeApplyResult previewApply(Long id) {
        return run(id, true);
    }

    /**
     * 일괄 반영 본체. {@code dryRun}이면 대상만 세고 쓰지 않는다.
     *
     * <p><b>예외로 등록된 행은 건너뛴다.</b> 담당자가 개별 수정한 행은 의도적으로 다른 값이라,
     * 일괄적용 한 번에 조용히 덮이면 그 상품이 잘못된 단가로 청구된다.
     */
    private WorkTypeApplyResult run(Long id, boolean dryRun) {
        WorkType w = getOrThrow(id);
        Set<Integer> exceptions = new HashSet<>();
        overrideRepository.findAll().forEach(o -> exceptions.add(o.getDtlCd()));

        List<Integer> targets = new ArrayList<>();
        List<Integer> skipped = new ArrayList<>();

        // ① 매출프로그램 상품 단가(우리 DB) — DSRE 연동과 무관하게 항상 반영한다.
        //    ‼️이걸 빼면 버튼 하나가 **절반만** 반영한다. 담당자는 기준단가를 바꾸고 눌렀는데
        //    우리 상품 단가만 옛값으로 남아 있는 줄 모른다.
        int[] ours = productLogisRateService.applyWorkType(w.getPackType(), dryRun);

        // ② DSRE 시행 단가 — 연동이 꺼져 있으면 건너뛴다(막지 않는다).
        //    예전엔 여기서 400을 던져, DSRE가 없는 환경에서는 ①까지 통째로 불가능했다.
        DsreGateway gateway = dsreGatewayProvider.getIfAvailable();
        boolean dsreApplied = gateway != null;
        if (dsreApplied) {
            for (LogisCostRate r : gateway.listLogisCosts()) {
                if (r.packtype() != w.getPackType() || r.dtlCd() == 0) {
                    continue;   // 다른 작업구분 / 회수단가 특수행(dtl_cd=0)
                }
                if (exceptions.contains(r.dtlCd())) {
                    skipped.add(r.dtlCd());
                    continue;
                }
                targets.add(r.dtlCd());
                if (!dryRun) {
                    gateway.upsertLogisCost(r.dtlCd(), w.getPaper(), w.getOmr(), w.getEtc(),
                            w.getLabel(), w.getBasic(), w.getTrade(), w.getPackType(), r.bSpare());
                }
            }
        }
        return new WorkTypeApplyResult(w.getPackType(), w.getName(), dryRun,
                targets.size() + ours[0], targets, skipped.size() + ours[1], skipped,
                ours[0], dsreApplied);
    }

    /**
     * 물류단가의 작업구분이 <b>등록된 작업구분인지</b> 확인한다(B-13).
     *
     * <p>★예전엔 단가 DTO에 {@code @Min(1) @Max(3)}이 박혀 있었다. 그래서 작업구분 관리에서
     * 4번을 새로 만들 수는 있는데 그 번호로 단가를 넣으면 400이 났고, 벌크 경로엔 그 제약이
     * 아예 없어 <b>경로마다 다르게 굴었다</b>. 상한을 코드에 박는 대신
     * {@code work_type} 테이블을 단일 기준으로 삼는다 — 작업구분을 늘리면 단가도 따라 열린다.
     */
    public void assertRegisteredPackType(Integer packType) {
        if (packType == null) {
            return;   // 벌크에서 "미지정 = 기존값 유지"
        }
        if (workTypeRepository.findByPackType(packType).isEmpty()) {
            String valid = workTypeRepository.findAllByOrderBySortOrderAscPackTypeAsc().stream()
                    .map(w -> w.getPackType() + "(" + w.getName() + ")")
                    .collect(java.util.stream.Collectors.joining(", "));
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "등록되지 않은 작업구분입니다: " + packType
                            + ". 작업구분 관리에 먼저 등록하세요. 현재 사용 가능: " + valid);
        }
    }

    /**
     * 단가 행을 <b>개별 수정</b>했다고 표시한다 — 이후 일괄적용이 그 행을 건너뛴다.
     * 물류단가 단건 수정({@code PUT /logistics-costs/rates/{dtlCd}})에서 호출한다.
     */
    @Transactional
    public void markOverride(int dtlCd) {
        if (!overrideRepository.existsById(dtlCd)) {
            overrideRepository.save(LogisRateOverride.of(dtlCd, currentAuditor.username()));
        }
    }

    /** 예외 해제 — 다시 기준단가를 따르게 한다. */
    @Transactional
    public void clearOverride(int dtlCd) {
        overrideRepository.deleteById(dtlCd);
    }

    /** 예외로 등록된 시행코드 전부(화면에서 빨간색 표시용). */
    public Set<Integer> overriddenDtlCds() {
        Set<Integer> out = new HashSet<>();
        overrideRepository.findAll().forEach(o -> out.add(o.getDtlCd()));
        return out;
    }

    private WorkType getOrThrow(Long id) {
        return workTypeRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "작업구분이 없습니다. id=" + id));
    }
}
