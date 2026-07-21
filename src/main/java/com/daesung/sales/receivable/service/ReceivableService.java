package com.daesung.sales.receivable.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.PageResponse;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.receivable.dto.ArLedgerResponse;
import com.daesung.sales.receivable.dto.ArStatusResponse;
import com.daesung.sales.receivable.dto.CarryforwardResult;
import com.daesung.sales.receivable.dto.CollectionRequest;
import com.daesung.sales.receivable.dto.CollectionResponse;
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
    private final ReceivableCarryforwardRepository carryforwardRepository;
    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;

    /** 수금 등록. 수금번호(C) 채번. 어음정보는 유형=어음일 때만 저장(엔티티에서 처리). */
    @Transactional
    public CollectionResponse registerCollection(CollectionRequest req) {
        Partner partner = partnerRepository.findById(req.partnerId())
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                        "거래처가 없습니다. id=" + req.partnerId()));
        String collNo = "C-" + req.collDate().format(YYYYMMDD) + "-" + collectionRepository.nextCollectionSeq();
        Collection c = collectionRepository.save(Collection.create(
                collNo, req.collDate(), partner, req.collType(), req.collAmt(),
                req.promissoryNo(), req.promissoryDue(), req.bankName(), req.branchName(), req.memo()));
        return CollectionResponse.from(c);
    }

    /** 수금 목록 조회. */
    @Transactional(readOnly = true)
    public PageResponse<CollectionResponse> searchCollections(LocalDate from, LocalDate to,
                                                              Long partnerId, Pageable pageable) {
        return PageResponse.of(
                collectionRepository.search(from, to, partnerId, pageable).map(CollectionResponse::from));
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
        long tOpen = 0, tSale = 0, tRet = 0, tTax = 0, tGen = 0, tColl = 0, tBal = 0;
        for (Long pid : ids) {
            Partner p = partners.get(pid);
            if (p == null) {
                continue;
            }
            long opening = carry.getOrDefault(pid, 0L);
            long[] s = sales.getOrDefault(pid, new long[4]); // [gen, saleAmt, returnAmt, tax]
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
                    opening, s[1], s[2], s[3], s[0], collected, balance,
                    assureAmount, ratio, p.getAssureExpiry(), level));

            tOpen += opening; tSale += s[1]; tRet += s[2]; tTax += s[3]; tGen += s[0];
            tColl += collected; tBal += balance;
        }
        rows.sort((a, b) -> a.partnerCode().compareTo(b.partnerCode()));
        ArStatusResponse.Row total = new ArStatusResponse.Row(null, "합계", null,
                tOpen, tSale, tRet, tTax, tGen, tColl, tBal, null, null, null, null);
        return new ArStatusResponse(from, to, rows, total);
    }

    /**
     * 외상매출장(단일 거래처 러닝밸런스). 기초이월(시작일 직전 잔액) + 기간 내 매출/반품/수금 명세 + 일자별 누계.
     * 누계 = 이월 + Σ(채권 증감). 매출/교사용/증정 +total, 반품 −total, 수금 −collAmt.
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
        record Entry(LocalDate date, int ord, String kind, String refNo, String desc, long amount) {}
        List<Entry> entries = new ArrayList<>();
        for (Sale s : saleRepository.findLedgerLines(partnerId, from, to)) {
            long total = (s.getTotalAmount() == null) ? 0L : s.getTotalAmount();
            long amount = (s.getSalesCategory() == SalesCategory.RETURN) ? -total : total;
            entries.add(new Entry(s.getSalesDate(), 0, saleKind(s), s.getSalesNo(),
                    s.getProduct().getCode() + " " + s.getProduct().getName(), amount));
        }
        for (Collection c : collectionRepository.findLedgerLines(partnerId, from, to)) {
            entries.add(new Entry(c.getCollDate(), 1, "수금", c.getCollectionNo(),
                    collLabel(c.getCollType()), -c.getCollAmt()));
        }
        entries.sort(Comparator.comparing(Entry::date).thenComparingInt(Entry::ord));

        List<ArLedgerResponse.Line> lines = new ArrayList<>();
        long running = opening;
        for (Entry e : entries) {
            running += e.amount();
            lines.add(new ArLedgerResponse.Line(e.date(), e.kind(), e.refNo(), e.desc(), e.amount(), running));
        }
        return new ArLedgerResponse(partner.getId(), partner.getName(), from, to, opening, running, lines);
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

    /** receivableByPartner: [partnerId, gen, saleAmt, returnAmt, tax] → Map<partnerId, long[gen,sale,ret,tax]>. */
    private static Map<Long, long[]> toSalesMap(List<Object[]> rows) {
        Map<Long, long[]> m = new HashMap<>();
        for (Object[] r : rows) {
            m.put(num(r[0]), new long[]{num(r[1]), num(r[2]), num(r[3]), num(r[4])});
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
