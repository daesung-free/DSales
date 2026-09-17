package com.daesung.sales.receivable.service;

import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.receivable.dto.ArLedgerResponse;
import com.daesung.sales.receivable.dto.ArStatusResponse;
import com.daesung.sales.receivable.dto.CarryforwardResult;
import com.daesung.sales.receivable.dto.CollectionRequest;
import com.daesung.sales.receivable.dto.CollectionLedgerResponse;
import com.daesung.sales.receivable.dto.CollectionResponse;
import com.daesung.sales.receivable.dto.DuffLedgerResponse;
import com.daesung.sales.receivable.entity.Collection;
import com.daesung.sales.receivable.entity.CollectionType;
import com.daesung.sales.receivable.entity.ReceivableCarryforward;
import com.daesung.sales.receivable.repository.CollectionRepository;
import com.daesung.sales.receivable.repository.ReceivableCarryforwardRepository;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 채권/수금 도메인. 근거: 레거시 수금등록/외상매출현황조회 + 기획서[마감관리].
 *  - 수금 등록/조회, 이월 스냅샷 생성(idempotent), 미수금 현황(잔액=이월+채권발생−수금, 담보비율).
 * ★ 레거시의 "조회 부수효과 이월생성" 안티패턴 제거 — 이월은 명시 API로만 생성.
 */
@Service
@RequiredArgsConstructor
public class ReceivableService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final CollectionRepository collectionRepository;
    private final SequenceService sequenceService;
    private final ReceivableCarryforwardRepository carryforwardRepository;
    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final PeriodLockService periodLockService;
    private final com.daesung.sales.common.audit.CurrentAuditor currentAuditor;

    /** 수금 등록. 수금번호(C) 채번. 어음정보는 유형=어음일 때만 저장(엔티티에서 처리). */
    @Transactional
    public CollectionResponse registerCollection(CollectionRequest req) {
        periodLockService.assertNotLocked(req.collDate());
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));
        String collNo = "C-" + req.collDate().format(YYYYMMDD) + "-" + sequenceService.next(SequenceService.SEQ_COLLECTION);
        Collection c = collectionRepository.save(Collection.create(
                collNo, req.collDate(), req.writeDate(), partner, req.collKind(), req.collType(), req.collAmt(),
                req.promissoryNo(), req.promissoryDue(), req.bankName(), req.branchName(), req.memo()));
        return CollectionResponse.from(c);
    }

    /**
     * 조회기준 정규화. 미지정이면 수금일자(COLL).
     *
     * <p>‼️모르는 값은 거부한다. 조용히 기본값으로 넘기면 담당자는 기장일자로 본다고 믿는데
     * 실제로는 수금일자로 잘린 목록을 보게 된다 — 숫자가 달라지는데 오류가 안 난다.
     */
    private static String normalizeBasis(String raw) {
        if (raw == null || raw.isBlank()) {
            return "COLL";
        }
        // ‼️대소문자 변환으로 맞추지 않는다 — 조회 범위를 가르는 값이라
        //   로케일에 따라 다른 문자열이 같아지면 안 된다(정적분석 IMPROPER_UNICODE).
        String v = raw.trim();
        if ("COLL".equals(v) || "coll".equals(v)) {
            return "COLL";
        }
        if ("WRITE".equals(v) || "write".equals(v)) {
            return "WRITE";
        }
        throw new BusinessException(ErrorCode.INVALID_INPUT,
                "알 수 없는 조회기준입니다: " + raw + " (COLL=수금일자 / WRITE=기장일자)");
    }

    /** 수금 목록 조회. */
    @Transactional(readOnly = true)
    public PageResponse<CollectionResponse> searchCollections(LocalDate from, LocalDate to,
                                                              Long partnerId, String collKind,
                                                              CollectionType collType, String keyword,
                                                              String dateBasis, Pageable pageable) {
        String kind = (collKind == null || collKind.isBlank()) ? null : collKind.trim();
        String kw = com.daesung.sales.common.query.Keywords.norm(keyword);
        return PageResponse.of(collectionRepository
                .search(from, to, partnerId, kind, collType, normalizeBasis(dateBasis),
                        (kw == null) ? null : "%" + kw + "%", pageable)
                .map(CollectionResponse::from));
    }

    /**
     * 수금 수정(23p "CRUD 전체"). 수금번호·거래처는 불변 —
     * 거래처를 옮기면 두 거래처의 채권 잔액이 동시에 틀어진다(취소 후 재등록이 맞다).
     *
     * <p><b>바뀐 날짜와 원래 날짜 양쪽</b>의 월마감을 본다. 옮겨 갈 달만 검사하면
     * 마감된 달에서 열린 달로 빼내는 길이 열려, 마감 후에도 그 달 금액을 바꿀 수 있게 된다.
     */
    @Transactional
    public CollectionResponse updateCollection(Long id, CollectionRequest req) {
        Collection c = getCollectionOrThrow(id);
        periodLockService.assertNotLocked(c.getCollDate());     // 원래 귀속 월
        periodLockService.assertNotLocked(req.collDate());      // 옮겨 갈 월
        c.update(req.collDate(), req.writeDate(), req.collKind(), req.collType(), req.collAmt(),
                req.promissoryNo(), req.promissoryDue(), req.bankName(), req.branchName(), req.memo());
        return CollectionResponse.from(c);
    }

    /**
     * 수금 삭제(논리삭제). 레거시는 이 기능이 주석 처리돼 막혀 있었으나(수금관리.vb:356)
     * 정본 23p가 "CRUD 전체 가능 화면"을 요구한다.
     *
     * <p>물리삭제하지 않는 이유: 수금은 돈이 들어온 기록이라 지우면 그만큼 <b>채권 잔액이 늘어난다</b>.
     * 누가 언제 지웠는지 남지 않으면 잔액이 왜 달라졌는지 설명할 수 없다.
     */
    @Transactional
    public void deleteCollection(Long id) {
        Collection c = getCollectionOrThrow(id);
        periodLockService.assertNotLocked(c.getCollDate());
        c.markDeleted(currentAuditor.username());
    }

    private Collection getCollectionOrThrow(Long id) {
        return collectionRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "수금이 없습니다. id=" + id));
    }

    /**
     * 수금관리(23p) 조회 — 조회대상기준 3종 + 소계. 근거: 레거시 수금관리.vb:146~227.
     *
     * <p>레거시가 붙이는 소계는 정본이 적은 '일계'보다 촘촘하다 —
     * 날짜 기준은 <b>일계·월계·누계</b> 3단, 거래처 기준은 <b>소계(거래처코드 첫 글자 그룹)·누계</b>다.
     * 그대로 옮긴다.
     *
     * <p>페이징하지 않는다. 소계는 앞뒤 행이 다 있어야 성립해서 잘린 지점의 소계가 틀린 값이 된다.
     */
    @Transactional(readOnly = true)
    public CollectionLedgerResponse collectionLedger(CollectionLedgerResponse.Basis basis,
                                                     LocalDate fromDate, LocalDate toDate,
                                                     Long partnerId, String collKind,
                                                     CollectionType collType) {
        CollectionLedgerResponse.Basis b =
                (basis == null) ? CollectionLedgerResponse.Basis.WRITE_DATE : basis;   // 레거시 기본 선택
        boolean byWrite = (b == CollectionLedgerResponse.Basis.WRITE_DATE);
        boolean byPartner = (b == CollectionLedgerResponse.Basis.PARTNER);
        String kind = (collKind == null || collKind.isBlank()) ? null : collKind.trim();

        List<Collection> src = collectionRepository.ledger(
                fromDate, toDate, partnerId, kind, collType, byWrite, byPartner);

        List<CollectionLedgerResponse.Row> rows = new ArrayList<>();
        long running = 0;
        long dayOrGroup = 0;
        long month = 0;
        String prevDay = null;
        String prevKey = null;

        for (Collection c : src) {
            String day = String.valueOf(byWrite ? c.getWriteDate() : c.getCollDate());
            // 거래처 기준의 그룹 키는 거래처코드 첫 글자다(레거시 custCode.Substring(0,1)).
            String key = byPartner ? groupOf(c.getPartner().getCode()) : day;

            if (prevKey != null && !prevKey.equals(key)) {
                if (byPartner) {
                    rows.add(CollectionLedgerResponse.Row.subtotal("GROUP_SUBTOTAL", "소 계", dayOrGroup));
                    rows.add(CollectionLedgerResponse.Row.subtotal("RUNNING_TOTAL", "누 계", running));
                } else {
                    rows.add(CollectionLedgerResponse.Row.subtotal("DAY_SUBTOTAL", "일 계", dayOrGroup));
                    if (!month(prevDay).equals(month(day))) {   // 달이 바뀌면 월계·누계도(레거시 순서 그대로)
                        rows.add(CollectionLedgerResponse.Row.subtotal("MONTH_SUBTOTAL", "월 계", month));
                        rows.add(CollectionLedgerResponse.Row.subtotal("RUNNING_TOTAL", "누 계", running));
                        month = 0;
                    }
                }
                dayOrGroup = 0;
            }

            rows.add(detailRow(c));
            dayOrGroup += c.getCollAmt();
            month += c.getCollAmt();
            running += c.getCollAmt();
            prevDay = day;
            prevKey = key;
        }

        if (prevKey != null) {
            if (byPartner) {
                rows.add(CollectionLedgerResponse.Row.subtotal("GROUP_SUBTOTAL", "소 계", dayOrGroup));
            } else {
                rows.add(CollectionLedgerResponse.Row.subtotal("DAY_SUBTOTAL", "일 계", dayOrGroup));
                rows.add(CollectionLedgerResponse.Row.subtotal("MONTH_SUBTOTAL", "월 계", month));
            }
            rows.add(CollectionLedgerResponse.Row.subtotal("RUNNING_TOTAL", "누 계", running));
        }

        return new CollectionLedgerResponse(b, b.label(), fromDate, toDate, rows, running);
    }

    /**
     * 외상매출장 '더프모만'(24p). 근거: 레거시 외상매출장조회.vb:603 {@code Refresh_DataGridView_더프모만()}.
     *
     * <p>정본이 "그리드 컬럼 구조 자체가 완전 전환"이라고 못박아, 기본 장부와 <b>다른 응답</b>으로 낸다.
     * 기본 장부는 채권 러닝밸런스를 보는 화면이고 이쪽은 모의고사 매출 세부라 잔액 개념이 없다.
     *
     * <p>소계는 레거시 {@code GROUP BY rollup(schCode, idx), 거래년월} 그대로
     * <b>학교(원) 계 → 월 계</b> 순으로 붙인다.
     */
    @Transactional(readOnly = true)
    public DuffLedgerResponse duffLedger(Long partnerId, LocalDate fromDate, LocalDate toDate) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + partnerId));
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        List<DuffLedgerResponse.Row> rows = new ArrayList<>();
        long schoolQty = 0;
        long schoolAmt = 0;
        long monthQty = 0;
        long monthAmt = 0;
        long totalQty = 0;
        long totalAmt = 0;
        String prevMonth = null;
        String prevSchool = null;

        for (Object[] r : saleRepository.duffLedgerLines(partnerId, from, to)) {
            LocalDate date = toLocalDate(r[0]);
            String school = str(r[1]);
            String month = (date == null) ? "" : String.format("%04d-%02d", date.getYear(), date.getMonthValue());
            long qty = num(r[7]);
            long amt = num(r[8]);

            // 학교가 바뀌면 학교 계, 달이 바뀌면 월 계까지(레거시 rollup 순서 그대로).
            if (prevSchool != null && !prevSchool.equals(school)) {
                rows.add(DuffLedgerResponse.Row.subtotal("SCHOOL_SUBTOTAL", "학교(원) 계",
                        prevSchool, schoolQty, schoolAmt));
                schoolQty = 0;
                schoolAmt = 0;
            }
            if (prevMonth != null && !prevMonth.equals(month)) {
                rows.add(DuffLedgerResponse.Row.subtotal("MONTH_SUBTOTAL", "월 계", null, monthQty, monthAmt));
                monthQty = 0;
                monthAmt = 0;
            }

            rows.add(new DuffLedgerResponse.Row("DETAIL", null, date, school,
                    examMonth(toLocalDate(r[2])), str(r[3]), procLabel(str(r[4])),
                    intOrNull(r[5]), intOrNull(r[6]), qty, amt));

            schoolQty += qty;
            schoolAmt += amt;
            monthQty += qty;
            monthAmt += amt;
            totalQty += qty;
            totalAmt += amt;
            prevSchool = school;
            prevMonth = month;
        }

        if (prevSchool != null) {
            rows.add(DuffLedgerResponse.Row.subtotal("SCHOOL_SUBTOTAL", "학교(원) 계",
                    prevSchool, schoolQty, schoolAmt));
            rows.add(DuffLedgerResponse.Row.subtotal("MONTH_SUBTOTAL", "월 계", null, monthQty, monthAmt));
        }

        return new DuffLedgerResponse(partnerId, partner.getName(), from, to, rows, totalQty, totalAmt);
    }

    /**
     * 시행월(yyyy-MM). BOM 시행예정일이 없으면 <b>비운다</b> —
     * 레거시처럼 도서명을 잘라 만들면 이름 규칙이 어긋나는 순간 엉뚱한 값이 찍힌다(그쪽은 '##ERROR'였다).
     */
    private static String examMonth(LocalDate examDate) {
        return (examDate == null) ? null
                : String.format("%04d-%02d", examDate.getYear(), examDate.getMonthValue());
    }

    /** 성적처리 구분 → 화면 표기. 미지정은 '비처리'(37p와 같은 규칙). */
    private static String procLabel(String procType) {
        return "GRADED".equals(procType) ? "처리" : "비처리";
    }

    private static LocalDate toLocalDate(Object o) {
        if (o == null) {
            return null;
        }
        return (o instanceof java.sql.Date d) ? d.toLocalDate() : LocalDate.parse(o.toString());
    }

    private static Integer intOrNull(Object o) {
        return (o == null) ? null : ((Number) o).intValue();
    }

    private static String str(Object o) {
        return (o == null) ? null : o.toString();
    }

    /** 거래처 그룹 = 거래처코드 첫 글자(레거시 custCode.Substring(0,1)). 코드가 비면 빈 그룹. */
    private static String groupOf(String partnerCode) {
        return (partnerCode == null || partnerCode.isEmpty()) ? "" : partnerCode.substring(0, 1);
    }

    /** yyyy-MM. 날짜 문자열(yyyy-MM-dd)에서 잘라 쓴다 — 월계 경계 판정용. */
    private static String month(String day) {
        return (day == null || day.length() < 7) ? "" : day.substring(0, 7);
    }

    private static CollectionLedgerResponse.Row detailRow(Collection c) {
        return new CollectionLedgerResponse.Row("DETAIL", null, c.getId(), c.getCollectionNo(),
                c.getCollDate(), c.getWriteDate(),
                c.getPartner().getCode(), c.getPartner().getName(),
                c.getCollKind(), c.getCollType(), c.getCollType().label(), c.getCollAmt(),
                c.getPromissoryNo(), c.getPromissoryDue(), c.getBankName(), c.getBranchName(), c.getMemo());
    }

    /**
     * 채권 이월 스냅샷 생성(idempotent). fiscalYear 귀속 이월 = 전년말 채권 잔액.
     * 전년말 잔액 = 전전년 이월 + 전년 채권발생 − 전년 수금. 잔액≠0 거래처만 저장. 기존 연도분 삭제 후 재생성.
     */
    @Transactional
    public CarryforwardResult generateCarryforward(int fiscalYear) {
        int prevYear = fiscalYear - 1;
        LocalDate pyStart = LocalDate.of(prevYear, 1, 1);
        LocalDate pyEnd = LocalDate.of(prevYear, 12, 31);

        Map<Long, Long> carry = toAmountMap(carryforwardRepository.sumByYear(prevYear, null));
        Map<Long, Long> gen = toReceivableGenMap(saleRepository.receivableByPartner(pyStart, pyEnd, null));
        Map<Long, Long> coll = toAmountMap(collectionRepository.sumByPartner(pyStart, pyEnd, null));

        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(carry.keySet());
        ids.addAll(gen.keySet());
        ids.addAll(coll.keySet());

        int deleted = carryforwardRepository.deleteByFiscalYear(fiscalYear);
        carryforwardRepository.flush(); // 삭제 확정 후 삽입(유니크 제약 회피)

        LocalDateTime now = LocalDateTime.now();
        int generated = 0;
        long total = 0;
        for (Long pid : ids) {
            long balance = carry.getOrDefault(pid, 0L) + gen.getOrDefault(pid, 0L) - coll.getOrDefault(pid, 0L);
            if (balance == 0) {
                continue;
            }
            Partner partner = partnerRepository.getReferenceById(pid);
            carryforwardRepository.save(ReceivableCarryforward.create(fiscalYear, partner, balance, now));
            generated++;
            total += balance;
        }
        return new CarryforwardResult(fiscalYear, deleted, generated, total);
    }

    /**
     * 미수금 현황. 거래처별 잔액 = 이월(전년말 스냅샷) + 기간 채권발생 − 기간 수금 + 담보비율.
     * 기간 미지정 시 올해 1/1~오늘. 이월은 종료일 연도의 carryforward.
     */
    @Transactional(readOnly = true)
    public ArStatusResponse arStatus(LocalDate fromDate, LocalDate toDate, Long partnerId) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        Map<Long, Long> carry = toAmountMap(carryforwardRepository.sumByYear(to.getYear(), partnerId));
        Map<Long, long[]> sales = toSalesMap(saleRepository.receivableByPartner(from, to, partnerId));
        Map<Long, Long> coll = toAmountMap(collectionRepository.sumByPartner(from, to, partnerId));

        Set<Long> ids = new LinkedHashSet<>();
        ids.addAll(carry.keySet());
        ids.addAll(sales.keySet());
        ids.addAll(coll.keySet());

        Map<Long, Partner> partners = new HashMap<>();
        partnerRepository.findAllById(ids).forEach(p -> partners.put(p.getId(), p));

        List<ArStatusResponse.Row> rows = new ArrayList<>();
        long tOpen = 0, tSale = 0, tRet = 0, tTax = 0, tGen = 0, tColl = 0, tBal = 0, tTeacher = 0, tTeacherQty = 0;
        for (Long pid : ids) {
            Partner p = partners.get(pid);
            if (p == null) {
                continue;
            }
            long opening = carry.getOrDefault(pid, 0L);
            long[] s = sales.getOrDefault(pid, new long[6]); // [gen, saleAmt, returnAmt, tax, teacherAmt, teacherQty]
            long collected = coll.getOrDefault(pid, 0L);
            long balance = opening + s[0] - collected;

            Long assureAmount = p.getAssureAmount();
            Double ratio = null;
            String level = null;
            if (assureAmount != null && assureAmount > 0) {
                ratio = Math.round((double) balance / assureAmount * 100 * 10) / 10.0;
                level = ratio >= 100 ? "OVER" : ratio >= 70 ? "WARN" : ratio > 50 ? "WATCH" : "NORMAL";
            }
            rows.add(new ArStatusResponse.Row(pid, p.getCode(), p.getName(),
                    opening, s[1], s[2], s[3], s[0], collected, balance, s[4], s[5],
                    assureAmount, ratio, p.getAssureExpiry(), p.getAssureNote(), level));

            tOpen += opening; tSale += s[1]; tRet += s[2]; tTax += s[3]; tGen += s[0];
            tColl += collected; tBal += balance; tTeacher += s[4]; tTeacherQty += s[5];
        }
        rows.sort((a, b) -> a.partnerCode().compareTo(b.partnerCode()));
        ArStatusResponse.Row total = new ArStatusResponse.Row(null, "합계", null,
                tOpen, tSale, tRet, tTax, tGen, tColl, tBal, tTeacher, tTeacherQty,
                null, null, null, null, null);
        return new ArStatusResponse(from, to, rows, total);
    }

    /**
     * 외상매출장 — <b>전체 거래처</b>(24p). 거래처별 원장을 한 번에 낸다.
     *
     * <p>화면이 "전체 거래처 한 번에 보기는 준비 중"으로 막아 두고 있었다(2026-09-16 번들 실측).
     * 원장은 거래처 하나가 단위라 <b>거래처마다 한 벌씩</b> 담아 낸다 — 한 표로 합치면
     * 러닝밸런스가 남의 거래에 밀려 의미를 잃는다.
     *
     * <p>★대상 거래처는 <b>외상매출현황과 같은 집합</b>이다(이월이 있거나 기간 내 거래가 있는 곳).
     * 다른 기준으로 고르면 두 화면의 거래처 수가 달라져 어느 쪽도 못 믿는다.
     *
     * <p>‼️거래처마다 원장을 만들므로 거래처 수만큼 조회가 돈다. 기간을 넓게 잡으면 느리다 —
     * 화면이 기간을 좁혀 쓰도록 안내하는 편이 낫다.
     */
    @Transactional(readOnly = true)
    public List<ArLedgerResponse> arLedgerAll(LocalDate fromDate, LocalDate toDate) {
        List<ArLedgerResponse> out = new ArrayList<>();
        for (ArStatusResponse.Row r : arStatus(fromDate, toDate, null).rows()) {
            if (r.partnerId() == null) {
                continue;   // 합계행
            }
            out.add(arLedger(r.partnerId(), fromDate, toDate));
        }
        return out;
    }

    /**
     * 외상매출장(24p) — <b>도서 단위 명세</b> + 러닝밸런스.
     * 근거: 레거시 외상매출장조회.vb {@code Refresh_DataGridView()} + 정본 24p 데이터 항목.
     *
     * <p>기초이월(시작일 직전 잔액) + 기간 내 매출/교사용/반품/수금 명세 + 일자별 누계.
     * 누계 = 이월 + Σ(채권 증감). 매출·교사용 +, 반품·수금 −.
     *
     * <p>★교사용은 <b>무가 중 공급률이 있는 것</b>만이다(레거시 {@code 무상 AND supRate<>0}).
     * 공급률 0인 무가는 증정이라 채권과 무관하고, 합쳐 두면 교사용 수량이 부풀려진다.
     */
    @Transactional(readOnly = true)
    public ArLedgerResponse arLedger(Long partnerId, LocalDate fromDate, LocalDate toDate) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + partnerId));
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();

        long opening = balanceAsOf(partnerId, from.minusDays(1));

        // 매출/반품 라인 + 수금 라인을 일자순으로 병합(같은 날은 매출 먼저).
        // ★행은 도서 단위다(레거시 UNION 구조). 한 행에는 그 구분의 칸만 찬다 —
        //   매출 행이면 매출수량·금액·세액이 차고 교사용·반품·수금 칸은 비어 있다.
        record Entry(LocalDate date, int ord, ArLedgerResponse.Line line, long amount) {}
        List<Entry> entries = new ArrayList<>();

        for (Sale s : saleRepository.findLedgerLines(partnerId, from, to)) {
            long supply = nz(s.getSupplyAmount());
            long tax = nz(s.getTax());
            long total = nz(s.getTotalAmount());
            SalesCategory cat = s.getSalesCategory();
            boolean isReturn = cat == SalesCategory.RETURN;
            // ‼️교사용은 '무가 중 공급률이 있는 것'만이다(레거시: 무상 AND supRate<>0).
            //   공급률 0인 무가는 증정이라 채권과 무관하다 — 합치면 교사용 수량이 부풀려진다.
            boolean isTeacher = cat == SalesCategory.FREE
                    && s.getSupplyRate() != null && s.getSupplyRate() != 0;
            boolean isSale = cat == SalesCategory.SALE;

            long amount = isReturn ? -total : (isSale || isTeacher ? total : 0L);
            entries.add(new Entry(s.getSalesDate(), 0, new ArLedgerResponse.Line(
                    s.getSalesDate(), saleKind(s), s.getSalesNo(),
                    s.getProduct().getCatCode(), s.getProduct().getCatName(),
                    s.getProduct().getCode(), bookLabel(s),
                    s.getSupplyRate(),
                    isSale ? (long) s.getQty() : null, isSale ? supply : null, isSale ? tax : null,
                    isTeacher ? (long) s.getQty() : null, isTeacher ? total : null,
                    isReturn ? -(long) s.getQty() : null, isReturn ? -total : null,
                    null, amount, 0L), amount));
        }

        for (Collection c : collectionRepository.findLedgerLines(partnerId, from, to)) {
            entries.add(new Entry(c.getCollDate(), 1, new ArLedgerResponse.Line(
                    c.getCollDate(), "수금", c.getCollectionNo(),
                    null, null, null, collLabel(c.getCollType()), null,
                    null, null, null, null, null, null, null,
                    c.getCollAmt(), -c.getCollAmt(), 0L), -c.getCollAmt()));
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparingInt(Entry::ord));

        List<ArLedgerResponse.Line> lines = new ArrayList<>();
        long running = opening;
        for (Entry e : entries) {
            running += e.amount();
            ArLedgerResponse.Line l = e.line();
            // 누계는 정렬이 끝난 뒤에야 정해지므로 여기서 채운다.
            lines.add(new ArLedgerResponse.Line(l.date(), l.kind(), l.refNo(),
                    l.catCode(), l.catName(), l.productCode(), l.productName(), l.supplyRate(),
                    l.saleQty(), l.saleAmount(), l.tax(),
                    l.teacherQty(), l.teacherAmount(), l.returnQty(), l.returnAmount(),
                    l.collectAmount(), l.amount(), running));
        }
        return new ArLedgerResponse(partner.getId(), partner.getName(), from, to, opening, running, lines);
    }

    /**
     * 도서명 표기. 레거시 그대로 회차·학교를 붙인다 —
     * {@code 도서명 [3회] <강남대성학원>}. 같은 책이 회차·학교별로 여러 줄 나오므로
     * 이름만으로는 어느 줄인지 가릴 수 없다(외상매출장조회.vb:200~203).
     */
    private static String bookLabel(Sale s) {
        String name = s.getProduct().getName();
        Integer round = s.getBookRound();
        String school = s.getSchoolName();
        StringBuilder sb = new StringBuilder(name == null ? "" : name);
        if (round != null && round != 0) {
            sb.append(" [").append(round).append("회]");
        }
        if (school != null && !school.isBlank()) {
            sb.append(" <").append(school).append(">");
        }
        return sb.toString();
    }

    private static long nz(Long v) {
        return (v == null) ? 0L : v;
    }

    /** 특정 시점까지의 채권 잔액 = 이월(당해 스냅샷) + 당해 1/1~시점 채권발생 − 수금. */
    private long balanceAsOf(Long partnerId, LocalDate asOf) {
        int year = asOf.getYear();
        LocalDate yStart = LocalDate.of(year, 1, 1);
        long carry = firstAmount(carryforwardRepository.sumByYear(year, partnerId));
        long gen = firstAmount(saleRepository.receivableByPartner(yStart, asOf, partnerId));
        long coll = firstAmount(collectionRepository.sumByPartner(yStart, asOf, partnerId));
        return carry + gen - coll;
    }

    private static String saleKind(Sale s) {
        if (s.getSalesCategory() == SalesCategory.RETURN) {
            return "반품";
        }
        if (s.getSalesCategory() == SalesCategory.SALE) {
            return "매출";
        }
        return (s.getShipmentType() == ShipmentType.TEACHER_USE) ? "교사용" : "증정";
    }

    private static String collLabel(CollectionType t) {
        return switch (t) {
            case CASH -> "수금(현금)";
            case PROMISSORY -> "수금(어음)";
            case PREPAY -> "수금(선수금)";
            case REPLACE -> "수금(대체)";
        };
    }

    // ── 매핑 헬퍼 ───────────────────────────────────────────────
    /** [partnerId, amount] 단건(또는 0건) 목록에서 amount만. receivableByPartner의 gen도 index1이라 공용. */
    private static long firstAmount(List<Object[]> rows) {
        return rows.isEmpty() ? 0L : num(rows.get(0)[1]);
    }

    private static long num(Object o) {
        return (o == null) ? 0L : ((Number) o).longValue();
    }

    /** [partnerId, amount] 목록 → Map. */
    private static Map<Long, Long> toAmountMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(num(r[0]), num(r[1]));
        }
        return m;
    }

    /** receivableByPartner: [partnerId, gen, saleAmt, returnAmt, tax, teacherAmt, teacherQty] → Map<partnerId, long[..]>. */
    private static Map<Long, long[]> toSalesMap(List<Object[]> rows) {
        Map<Long, long[]> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(num(r[0]), new long[]{num(r[1]), num(r[2]), num(r[3]), num(r[4]), num(r[5]), num(r[6])});
        }
        return m;
    }

    /** receivableByPartner의 gen(index 1)만 뽑아 Map. */
    private static Map<Long, Long> toReceivableGenMap(List<Object[]> rows) {
        Map<Long, Long> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(num(r[0]), num(r[1]));
        }
        return m;
    }
}
