package com.daesung.sales.school.service;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.SchoolRefRow;
import com.daesung.sales.school.dto.SchoolCreateRequest;
import com.daesung.sales.school.dto.SchoolResponse;
import com.daesung.sales.school.dto.SchoolSyncResult;
import com.daesung.sales.school.dto.SchoolUpdateRequest;
import com.daesung.sales.school.entity.School;
import com.daesung.sales.school.entity.SchoolSource;
import com.daesung.sales.school.repository.SchoolRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 학교/학원 마스터(35p) + DSRE '가져오기'(보존형 동기화). */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SchoolService {

    private final SchoolRepository schoolRepository;
    private final StatusHistoryService statusHistoryService;

    /** DSRE 연동은 daesung.dsre.enabled=true일 때만 빈이 존재 → 없을 수 있으므로 Provider로 주입. */
    private final ObjectProvider<DsreGateway> dsreGateway;
    /** ‼️조회(readOnly) 안에서 쓰려면 프록시를 타야 한다 — 자기호출은 트랜잭션이 안 걸린다. */
    private final ObjectProvider<SchoolService> self;

    /**
     * 조회할 때마다 DSRE2 원본을 읽어 반영한다.
     *
     * <p>★거래처와 같은 이유다 — 사람이 [가져오기]를 눌러 맞추게 하면 결국 안 누른다.
     * ‼️여기서 나는 오류는 삼킨다. DSRE2가 잠깐 끊겼다고 학교 목록이 안 열리면 안 된다.
     * 수동 {@code POST /masters/schools/sync}는 결과 건수를 보고 싶을 때 쓰라고 남겨 둔다.
     */
    private void refreshFromDsreQuietly() {
        DsreGateway gateway = dsreGateway.getIfAvailable();
        if (gateway == null) {
            return;
        }
        try {
            self.getObject().merge(gateway.readSchoolRefs());
        } catch (RuntimeException e) {
            log.warn("학교 DSRE2 갱신 실패 — 기존 값으로 조회합니다", e);
        }
    }

    public PageResponse<SchoolResponse> findAll(String keyword, Pageable pageable) {
        refreshFromDsreQuietly();
        Page<School> page = (keyword == null || keyword.isBlank())
                ? schoolRepository.findAll(pageable)
                : schoolRepository.findBySchoolCodeContainingIgnoreCaseOrSchoolNameContainingIgnoreCase(
                        keyword, keyword, pageable);
        return PageResponse.of(page.map(SchoolResponse::from));
    }

    public SchoolResponse findById(Long id) {
        return SchoolResponse.from(getOrThrow(id));
    }

    /**
     * 학교/학원검색(29p). 레거시 {@code 학교검색.vb}와 같은 필터 4종·같은 컬럼.
     *
     * <p>★특약점명 검색은 <b>대표·모의고사·IC 세 축</b>을 다 뒤진다 —
     * 같은 학교라도 상품군에 따라 담당 특약점이 달라, 대표만 보면 못 찾는다.
     */
    @Transactional(readOnly = true)
    public java.util.List<com.daesung.sales.school.dto.SchoolSearchRow> search(
            String schoolCode, String schoolName, String region, String partnerName) {
        return schoolRepository.search(nullIfBlank(schoolCode), nullIfBlank(schoolName),
                        nullIfBlank(region), nullIfBlank(partnerName)).stream()
                .map(com.daesung.sales.school.dto.SchoolSearchRow::from)
                .toList();
    }

    /**
     * 빈 문자열은 조건에서 뺀다. 레거시는 {@code like '%%'}로 넘겼는데,
     * 그러면 그 컬럼이 NULL인 행이 통째로 빠진다(SQL에서 {@code NULL like '%%'}는 참이 아니다).
     */
    private static String nullIfBlank(String v) {
        return (v == null || v.isBlank()) ? null : v;
    }

    @Transactional
    public SchoolResponse create(SchoolCreateRequest req) {
        String custCode = (req.custCode() == null) ? "" : req.custCode();
        schoolRepository.findByCustCodeAndSchoolCode(custCode, req.schoolCode()).ifPresent(s -> {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "이미 존재하는 거래처×학교 조합: " + custCode + "/" + req.schoolCode());
        });
        School school = School.create(req.schoolCode(), req.custCode(), req.custName(), req.city(), req.region(),
                req.schoolName(), req.isSchool() == null || req.isSchool(), req.schoolType(),
                req.clientCategory(), req.memo());
        // 학교/학원검색(29p) 축 — DSRE가 주지 않는 값이라 등록 시 함께 받는다.
        school.applySearchFields(req.cityCode(), req.partnerLoc(),
                req.mockPartnerCode(), req.mockPartnerName(),
                req.icPartnerCode(), req.icPartnerName());
        return SchoolResponse.from(schoolRepository.save(school));
    }

    @Transactional
    public SchoolResponse update(Long id, SchoolUpdateRequest req) {
        School school = getOrThrow(id);
        school.update(req.custCode(), req.custName(), req.city(), req.region(), req.schoolName(),
                req.isSchool() == null || req.isSchool(), req.schoolType(), req.clientCategory(), req.memo());
        return SchoolResponse.from(school);
    }

    // ── DSRE 가져오기(보존형 동기화) ──────────────────────────────────────

    /**
     * DSRE2 지사↔학교 매핑을 읽어 병합. DSRE 연동이 꺼져 있으면 오류.
     */
    @Transactional
    public SchoolSyncResult syncFromDsre() {
        DsreGateway gateway = dsreGateway.getIfAvailable();
        if (gateway == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "DSRE 연동이 비활성 상태입니다(daesung.dsre.enabled=false).");
        }
        return merge(gateway.readSchoolRefs());
    }

    /**
     * 보존형 병합(발주처 확정 2026-08, 35p) — <b>전체삭제 후 재수입 금지</b>.
     *
     * <p>이전 방식은 동기화 한 번에 수기 입력값(거래처구분·학교/학원구분·메모)과
     * 매출프로그램 전용 거래처가 통째로 날아갔다. 그래서 규칙이 넷이다.
     * <ol>
     *   <li>매칭키 (거래처코드, 학교코드)가 있으면 → <b>DSRE 관리 필드만</b> 덮어쓰고 수기 필드는 유지
     *   <li>없으면 → 신규 추가(source=DSRE)
     *   <li>DSRE2에서 사라진 DSRE 행 → 삭제하지 않고 미사용(active=false). 과거 매출 이력과 끊기면 안 됨
     *   <li>매출프로그램 전용(MANUAL) 행 → 동기화 대상에서 제외. 미사용 처리도 하지 않음
     * </ol>
     *
     * <p>{@code merge}를 별도 공개 메서드로 둔 이유: DSRE 연결 없이도 이 병합 규칙을 테스트로 고정하기 위함.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public SchoolSyncResult merge(List<SchoolRefRow> rows) {
        int added = 0;
        int updated = 0;
        int missingPartnerInfo = 0;
        Set<String> incoming = new HashSet<>();

        for (SchoolRefRow row : rows) {
            String custCode = (row.custCode() == null) ? "" : row.custCode();
            incoming.add(key(custCode, row.schoolCode()));
            if (row.custName() == null) {
                missingPartnerInfo++;   // DSRE2 고아 매핑 — 버리지 않고 건수만 드러낸다
            }

            School school = schoolRepository.findByCustCodeAndSchoolCode(custCode, row.schoolCode()).orElse(null);
            if (school == null) {
                School created = School.create(row.schoolCode(), custCode, row.custName(), row.city(), row.region(),
                        row.schoolName(), row.school(), null, null, null).fromDsre();
                schoolRepository.save(created);
                added++;
            } else {
                // 수기 필드(거래처구분·학교/학원구분·메모)는 건드리지 않는다.
                school.applyDsreFields(row.custName(), row.city(), row.region(), row.schoolName(), row.school());
                updated++;
            }
        }

        // DSRE2에서 사라진 행 = 미사용. MANUAL 행은 애초에 조회 대상이 아니라 안전하다.
        int deactivated = 0;
        for (School s : schoolRepository.findBySource(SchoolSource.DSRE)) {
            if (s.isActive() && !incoming.contains(key(s.getCustCode(), s.getSchoolCode()))) {
                s.markUnused();
                statusHistoryService.record(StatusEntityType.SCHOOL, s.getId(), "active",
                        true, false, "DSRE 동기화: 원본에서 사라져 미사용 처리");
                deactivated++;
            }
        }
        int keptManual = schoolRepository.findBySource(SchoolSource.MANUAL).size();

        return new SchoolSyncResult(rows.size(), added, updated, deactivated, keptManual, missingPartnerInfo);
    }

    private static String key(String custCode, String schoolCode) {
        return custCode + " " + schoolCode;
    }

    private School getOrThrow(Long id) {
        return schoolRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "학교가 없습니다. id=" + id));
    }
}
