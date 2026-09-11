package com.daesung.sales.partner.service;

import com.daesung.sales.audit.entity.MasterEntityType;
import com.daesung.sales.audit.service.MasterChangeLogService;
import com.daesung.sales.common.code.MasterCodes;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.dsre.gateway.ClientRefRow;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.partner.dto.ClientSyncResult;
import com.daesung.sales.partner.dto.CollateralExpiryResponse;
import com.daesung.sales.partner.dto.PartnerCreateRequest;
import com.daesung.sales.partner.dto.PartnerResponse;
import com.daesung.sales.partner.dto.PartnerUpdateRequest;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PartnerService {

    private final PartnerRepository partnerRepository;
    private final ObjectProvider<DsreGateway> dsreGateway;
    private final MasterChangeLogService masterChangeLogService;

    /**
     * 담보 만기 알림: 기준일 대비 담보 만기일이 withinDays 이내(또는 이미 만료)인 거래처.
     *
     * <p>정렬은 <b>만료(EXPIRED) 먼저, 그 다음 임박(IMMINENT)</b>이고 각 묶음 안에서는 만기일 순이다.
     * 근거: 발주처 확정(자료요청서 3-2(바)) — "만기 경과 건은 빨간색 표시 후 <b>별도 목록으로 쏘팅</b>".
     * 만기일 순으로만 두면 이미 지난 건이 목록 위쪽에 섞여, 아직 시간이 있는 건과 구분이 안 된다.
     */
    public CollateralExpiryResponse collateralExpiry(LocalDate asOf, int withinDays) {
        LocalDate reference = (asOf != null) ? asOf : LocalDate.now();
        LocalDate threshold = reference.plusDays(withinDays);
        List<CollateralExpiryResponse.Row> rows =
                partnerRepository.findByAssureExpiryLessThanEqualOrderByAssureExpiryAsc(threshold).stream()
                        .map(p -> {
                            long days = ChronoUnit.DAYS.between(reference, p.getAssureExpiry());
                            String status = (days < 0) ? "EXPIRED" : "IMMINENT";
                            return new CollateralExpiryResponse.Row(p.getId(), p.getCode(), p.getName(),
                                    p.getAssureExpiry(), p.getAssureAmount(), days, status);
                        })
                        // 만료분을 앞으로(경과일이 큰 순), 그 뒤에 임박분(만기일 가까운 순).
                        .sorted(Comparator
                                .comparing((CollateralExpiryResponse.Row r) -> !"EXPIRED".equals(r.status()))
                                .thenComparing(CollateralExpiryResponse.Row::assureExpiry))
                        .toList();
        return new CollateralExpiryResponse(reference, withinDays, rows);
    }

    /**
     * 거래처 목록. 레거시 거래처관리와 동일하게 <b>거래중(만료일 없음)만 기본 노출</b>하고,
     * includeExpired=true면 만료된 거래처까지 보여준다(화면의 '만료된 거래처 포함' 체크박스).
     * 근거: 거래처관리.vb — {@code where len(endDate) = 0} / {@code >= 0} 분기.
     */
    public PageResponse<PartnerResponse> findAll(String keyword, boolean includeExpired, Pageable pageable) {
        String kw = (keyword == null || keyword.isBlank()) ? null : keyword.trim();
        return PageResponse.of(partnerRepository.search(kw, includeExpired, pageable)
                .map(PartnerResponse::from));
    }

    public PartnerResponse findById(Long id) {
        return PartnerResponse.from(getOrThrow(id));
    }

    /**
     * DSRE2 거래처 원본을 읽어 병합. DSRE 연동이 꺼져 있으면 오류.
     *
     * <p>★<b>거래처 실데이터는 DSRE2가 원본이다.</b> 우리 마스터의 {@code P-SEOUL} 같은 행은
     * 사업자번호가 {@code 000-01-0000n} 연번인 테스트 시드다 — 손으로 채워 봐야 가짜를 채우는 것이다.
     * ‼️학교 동기화가 {@code tbl_cust_info}를 조인하므로 <b>거래처를 먼저</b> 맞춰야
     * 학교에 거래처명·도시가 붙는다(순서가 거꾸로면 학교가 빈 이름으로 들어온다).
     */
    @Transactional
    public ClientSyncResult syncFromDsre() {
        DsreGateway gateway = dsreGateway.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "DSRE 연동이 비활성 상태입니다(daesung.dsre.enabled=false).");
        }
        return merge(gateway.readClientRefs());
    }

    /**
     * 보존형 병합 — 학교 동기화와 같은 규칙이다.
     *
     * <ol>
     *   <li>거래처코드가 있으면 → <b>DSRE 관리 항목만</b> 덮어쓰고 담보·거래처구분은 유지
     *   <li>없으면 → 신규 추가
     *   <li>DSRE2에 없는 우리 쪽 거래처 → <b>손대지 않는다</b>. 수기로 만든 것일 수 있고,
     *       거래처는 과거 매출이 전부 FK로 물려 있어 잘못 건드리면 장부가 끊긴다
     * </ol>
     *
     * <p>{@code merge}를 따로 열어 둔 이유: DSRE 연결 없이 이 규칙을 테스트로 고정하기 위함
     * (학교 쪽과 같은 이유다).
     */
    @Transactional
    public ClientSyncResult merge(List<ClientRefRow> rows) {
        int added = 0;
        int updated = 0;
        int expired = 0;
        Set<String> incoming = new HashSet<>();

        for (ClientRefRow row : rows) {
            if (row.code() == null || row.code().isBlank()) {
                continue;   // 코드 없는 행은 매칭할 길이 없다
            }
            incoming.add(row.code());
            if (row.expired()) {
                expired++;
            }
            Partner partner = partnerRepository.findByCode(row.code()).orElse(null);
            if (partner == null) {
                partner = partnerRepository.save(
                        Partner.create(row.code(), row.name(), com.daesung.sales.partner.entity.PartnerType.NORMAL));
                added++;
            } else {
                updated++;
            }
            partner.applyDsreFields(row.name(), row.name1(), row.cityName(), row.region(),
                    row.bizNo(), row.bossName(), row.bizStatus(), row.bizItem(),
                    row.tel1(), row.tel2(), row.cellPhone(), row.fax(),
                    row.email1(), row.email2(), row.zip(), row.addr(),
                    row.expired());
        }

        int keptLocal = 0;
        for (Partner p : partnerRepository.findAll()) {
            if (!incoming.contains(p.getCode())) {
                keptLocal++;
            }
        }
        return new ClientSyncResult(rows.size(), added, updated, keptLocal, expired);
    }

    @Transactional
    public PartnerResponse create(PartnerCreateRequest req) {
        partnerRepository.findByCode(req.code()).ifPresent(p -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 존재하는 거래처코드: " + req.code());
        });
        Partner partner = Partner.create(req.code(), req.name(), req.type());
        partner.applyNames(req.cityName(), req.name1(), req.region(), MasterCodes.clientCategory(req.clientCategory()));
        partner.updateContact(req.bossId(), req.tel1(), req.tel2(), req.cellPhone(), req.fax(),
                req.zip(), req.zone2(), req.startDate(), req.endDate());
        // 등록 시점에 사업자정보도 함께 받는다 — 비워 두면 계산서·거래명세서가 빈 칸으로 나간다.
        partner.updateTaxInfo(req.bizNo(), req.bossName(), req.addr1(), req.addr2(),
                req.bizStatus(), req.bizItem(), req.email1(), req.email2());
        // ★담보도 여기서 저장한다. 예전엔 수정에만 있어서, 신규 등록 화면에 입력란이 있는데도
        //   값이 조용히 버려졌다 — 담보가 비면 외상매출현황의 비율·경고등급이 통째로 안 나온다.
        partner.updateCredit(req.assureAmount(), req.assureExpiry(), req.assureNote());
        return PartnerResponse.from(partnerRepository.save(partner));
    }

    /**
     * 거래처 수정. 바뀐 필드는 <b>변경이력에 남는다</b>(발주처 확정 3-1 라).
     *
     * <p>스냅샷은 반드시 <b>수정 전에</b> 뜬다 — JPA 영속 엔티티라 값을 바꾼 뒤 뜨면
     * before와 after가 같은 것을 가리켜 변경이 전혀 잡히지 않는다.
     */
    @Transactional
    public PartnerResponse update(Long id, PartnerUpdateRequest req) {
        Partner partner = getOrThrow(id);
        Map<String, String> before = partner.auditSnapshot();
        partner.update(req.name(), req.cityName(), req.name1(), req.region(), MasterCodes.clientCategory(req.clientCategory()), req.type());
        partner.updateCredit(req.assureAmount(), req.assureExpiry(), req.assureNote());
        partner.updateTaxInfo(req.bizNo(), req.bossName(), req.addr1(), req.addr2(),
                req.bizStatus(), req.bizItem(), req.email1(), req.email2());
        partner.updateContact(req.bossId(), req.tel1(), req.tel2(), req.cellPhone(), req.fax(),
                req.zip(), req.zone2(), req.startDate(), req.endDate());
        masterChangeLogService.recordDiff(MasterEntityType.PARTNER, partner.getId(), partner.getCode(),
                before, partner.auditSnapshot());
        return PartnerResponse.from(partner);
    }

    private Partner getOrThrow(Long id) {
        return partnerRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처가 없습니다. id=" + id));
    }

    /** 쓰이는 지역 목록(선택용). 마스터가 아니라 사용 중인 값의 집계다. */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> usedRegions() {
        return partnerRepository.findUsedRegions().stream()
                .map(u -> java.util.Map.<String, Object>of("value", u.getValue(), "usedCount", u.getUsedCount()))
                .toList();
    }

    /** 쓰이는 관할지역 목록. region과 별개 축이라 따로 낸다. */
    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> usedZones() {
        return partnerRepository.findUsedZones().stream()
                .map(u -> java.util.Map.<String, Object>of("value", u.getValue(), "usedCount", u.getUsedCount()))
                .toList();
    }

}
