package com.daesung.sales.sale.service;

import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.DuffChargeMode;
import com.daesung.sales.dsre.gateway.DuffSalesRow;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.DuffImportResponse;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 매출일괄등록(14p, 더프). DSRE2 신청내역을 <b>응시인원 기반</b>으로 읽어 매출을 만든다.
 * 근거: 레거시 {@code 매출가져오기.vb} + 정본 14p.
 *
 * <p>★교재({@link BulkSalesImportService})와 별개다. 교재는 {@code tbf_booklist_cnt}의
 * <b>부수</b>를 읽고, 더프는 {@code tbl_request_info}의 <b>인원</b>을 읽는다 —
 * 정본 14p 원문 "더프는 응시인원 기반 종량제 과금(타 상품군의 SET수량 기반과 계산방식 상이)".
 *
 * <h3>청구인원을 고르는 규칙</h3>
 * 정본 14p 원문: "처리구분(Y처리인원/N신청인원)·청구구분(T처리+비처리/S처리만/R등록)에 따라
 * 단가·청구인원 결정, <b>우선순위: 등록→처리비처리→신청(기본값)</b>".
 * 레거시 CASE({@code 매출가져오기.vb:425})가 그 우선순위의 실물이다 —
 * <ol>
 *   <li>청구구분 R → <b>등록인원</b></li>
 *   <li>조회 모드가 처리/비처리로 좁혀져 있으면 → 그 인원</li>
 *   <li>처리구분 N → <b>비처리인원</b></li>
 *   <li>그 외 → <b>처리인원</b></li>
 * </ol>
 *
 * <p>⚠️레거시 화면 안내문은 "처리구분 : Y - 처리인원 / <b>N - 신청인원</b>"이라고 적혀 있지만
 * ({@code 매출가져오기.Designer.vb:470}), 실제 CASE는 N일 때 <b>비처리인원</b>을 쓴다.
 * 안내문이 아니라 코드를 따랐다 — 실제로 청구된 금액이 그쪽이기 때문이다.
 *
 * <h3>레거시와 다르게 한 것</h3>
 * <ul>
 *   <li><b>멱등</b>: 소스키(모드:신청:거래처:학교:과목) UNIQUE로 재실행 시 중복 매출을 막는다.
 *       레거시는 같은 기간을 두 번 등록하면 매출이 두 벌 생긴다.</li>
 *   <li><b>미리보기</b>(dryRun): 쓰기 없이 대상·금액을 먼저 확인한다.</li>
 *   <li><b>월마감 검사</b>: 마감된 달로 매출이 흘러 들어가지 못하게 막는다.</li>
 *   <li><b>write-back 없음</b>: 레거시도 하지 않는다(교재와 달리 state를 건드리지 않는다).
 *       중복 방지는 위 소스키가 맡는다.</li>
 * </ul>
 */
@Service
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DuffSalesImportService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final DsreGateway dsreGateway;
    private final SequenceService sequenceService;
    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final PeriodLockService periodLockService;

    /** 청구인원 판정 결과 — 몇 명을, 무슨 근거로. */
    record Charge(int inwon, String basis) {}

    @Transactional
    public DuffImportResponse importSales(LocalDate fromDate, LocalDate toDate,
                                          DuffChargeMode mode, boolean onlyComplete, boolean dryRun) {
        LocalDate from = (fromDate != null) ? fromDate : LocalDate.now().withDayOfYear(1);
        LocalDate to = (toDate != null) ? toDate : LocalDate.now();
        DuffChargeMode m = (mode != null) ? mode : DuffChargeMode.ALL;

        // 실제 적재만 마감 검사 — 미리보기는 마감된 달도 들여다볼 수 있어야 한다.
        if (!dryRun) {
            periodLockService.assertNotLocked(to);
        }

        List<DuffImportResponse.Line> lines = new ArrayList<>();
        int imported = 0, skipped = 0, unmapped = 0, zeroInwon = 0, zeroAmount = 0;
        long totalSum = 0;

        for (DuffSalesRow row : dsreGateway.readDuffSales(from, to, onlyComplete, m)) {
            if (!m.accepts(row.procType(), row.chargeType())) {
                continue;   // 이 모드에서는 안 보이는 행(레거시 ifCondition)
            }

            Charge charge = charge(row, m);

            // 소스키에 모드를 넣는 이유: 같은 신청을 '처리'로 한 번, '비처리'로 한 번
            // 청구하는 계약(청구구분 T)이 실재한다. 모드를 빼면 두 번째가 중복으로 막힌다.
            String sourceKey = "DUFF:" + m.name() + ":" + row.reqCd() + ":" + row.custCode()
                    + ":" + row.schoolCode() + ":" + row.dtlCode();

            Partner partner = partnerRepository.findByCode(row.machulCode()).orElse(null);
            Product product = productRepository.findByCode(row.dtlCode()).orElse(null);

            // 정가·공급률은 DSRE 값이 우선이고, 없을 때만 도서 마스터로 채운다.
            // ★조용히 0원 매출을 만들지 않기 위해서다 — DSRE의 tbl_product_amt·tbl_school_ref가
            //   비어 있는 신청이 실재한다(복제본 7월 실측: 공급률 미매칭 다수).
            //   레거시는 그대로 0원을 넣지만, 0원 매출은 나중에 "매출 누락"으로만 드러난다.
            //   우리 매출등록(13p)이 이미 쓰는 폴백 규칙과 같게 맞췄다.
            int price = (row.price() > 0) ? row.price() : priceOf(product);
            int rate = (row.supplyRate() > 0) ? row.supplyRate() : rateOf(product);

            // ‼️총금액은 인원을 넣어 <b>한 번에</b> 계산한다. 단가를 먼저 버림하고 인원을 곱하면
            //   버림이 인원 수만큼 누적된다({@link Amounts} 주석의 999·33·3 사례와 같은 함정).
            //   unit은 화면 표시용이라 따로 낸다 — 총금액을 unit에서 되짚지 말 것.
            long total = Amounts.supplyOf(price, rate, charge.inwon(), discountOrNull(row));
            long unit = Amounts.supplyOf(price, rate, 1, discountOrNull(row));
            DuffSalesRow shown = withAmounts(row, price, rate);

            // 인원이 0 이하면 등록하지 않는다(정본 14p "인원 0 미만 건은 매출 등록 제외").
            if (charge.inwon() <= 0) {
                zeroInwon++;
                lines.add(line(shown, charge, unit, total, "ZERO_INWON", null));
                continue;
            }
            if (partner == null || product == null) {
                // ‼️예전엔 둘을 합쳐 "UNMAPPED" 하나로만 알려줬다. 그래서 화면은 전부
                //   "거래처 매핑 실패"로 읽었고, 실제 원인(상품 미등록)을 아무도 몰랐다
                //   — 2026-09-18 에 "매핑 키를 machulCode 로 바꿔 달라"는 요청이 온 것이
                //   그 오해다(우리는 이미 machulCode 로 찾고 있었다).
                //   무엇이 없는지 갈라서 알려준다. 담당자가 채울 마스터가 달라진다.
                unmapped++;
                String why = (partner == null && product == null)
                        ? "거래처(매출코드 " + row.machulCode() + ")와 상품(과목코드 "
                                + row.dtlCode() + ")이 모두 마스터에 없습니다"
                        : (partner == null)
                                ? "거래처가 마스터에 없습니다 — 매출코드 " + row.machulCode()
                                : "상품이 마스터에 없습니다 — 과목코드 " + row.dtlCode();
                String code = (partner == null && product == null) ? "UNMAPPED"
                        : (partner == null) ? "UNMAPPED_PARTNER" : "UNMAPPED_PRODUCT";
                lines.add(line(shown, charge, unit, total, code, why, null));
                continue;
            }
            // 폴백까지 쓰고도 금액이 0이면 등록하지 않는다. 단가가 어디에도 없다는 뜻이라
            // 담당자가 마스터를 채워야 할 건이다 — 0원으로 넣어두면 그 사실이 묻힌다.
            if (total <= 0) {
                zeroAmount++;
                lines.add(line(shown, charge, unit, total, "ZERO_AMOUNT", null));
                continue;
            }
            if (saleRepository.existsByBulkImportKey(sourceKey)) {
                skipped++;
                lines.add(line(shown, charge, unit, total, "SKIPPED", null));
                continue;
            }
            if (dryRun) {
                totalSum += total;
                lines.add(line(shown, charge, unit, total, "PREVIEW", null));
                continue;
            }

            // 세액은 자동산출하지 않는다(발주처 확정 2026-08-06). DSRE에 입력 경로가 없어 0.
            long tax = 0L;
            String salesNo = "I-" + to.format(YYYYMMDD) + "-"
                    + sequenceService.next(SequenceService.SEQ_INVOICE);
            Sale sale = Sale.createBulk(salesNo, row.reqDate() != null ? row.reqDate() : to,
                    partner, product, ShipmentType.NORMAL_SHIP, SalesCategory.SALE,
                    price, rate, charge.inwon(),
                    total, tax, total + tax, row.memo(), sourceKey);
            sale.applyUploadDetail(row.schoolCode(), row.schoolName(), null);
            sale.linkOrder(row.reqCd());   // 주문 목록이 "무슨 매출이 됐나"를 역참조한다
            sale.applyDiscount(discountOrNull(row));
            saleRepository.save(sale);

            imported++;
            totalSum += total;
            lines.add(line(shown, charge, unit, total, "IMPORTED", salesNo));
        }

        return new DuffImportResponse(dryRun, m.name(), imported, skipped, unmapped, zeroInwon,
                zeroAmount, totalSum, lines);
    }

    /**
     * 청구인원 판정. 레거시 {@code 매출가져오기.vb:425} CASE와 같은 순서다.
     * 순서가 곧 우선순위라 위에서 걸리면 아래는 보지 않는다.
     */
    static Charge charge(DuffSalesRow row, DuffChargeMode mode) {
        if ("R".equals(row.chargeType())) {
            return new Charge(row.regInwon(), "REGISTERED");
        }
        if (mode == DuffChargeMode.PROCESSED) {
            return new Charge(row.procInwon(), "PROCESSED");
        }
        if (mode == DuffChargeMode.UNPROCESSED) {
            return new Charge(row.unprocInwon(), "UNPROCESSED");
        }
        if ("N".equals(row.procType())) {
            return new Charge(row.unprocInwon(), "UNPROCESSED");
        }
        return new Charge(row.procInwon(), "PROCESSED");
    }

    /** 도서 마스터 정가(없으면 0). DSRE에 정가가 없을 때만 쓰는 폴백. */
    private static int priceOf(Product product) {
        return (product == null || product.getPrice() == null) ? 0 : product.getPrice();
    }

    /** 도서 마스터 공급률(없으면 0). DSRE에 공급률이 없을 때만 쓰는 폴백. */
    private static int rateOf(Product product) {
        return (product == null || product.getSupplyRate() == null) ? 0 : product.getSupplyRate();
    }

    /**
     * 화면에 보여줄 정가·공급률을 폴백 적용 후 값으로 바꾼 사본.
     * 계산에 쓴 값과 화면에 찍히는 값이 다르면 담당자가 금액을 검산할 수 없다.
     */
    private static DuffSalesRow withAmounts(DuffSalesRow r, int price, int rate) {
        return new DuffSalesRow(r.reqCd(), r.reqDate(), r.machulCode(), r.custCode(),
                r.custName(), r.custFullName(), r.cityName(), r.schoolCode(), r.schoolName(),
                r.grade(), r.prodCode(), r.prodName(), r.dtlCode(), r.dtlName(), r.subjectCnt(),
                r.procType(), r.chargeType(), r.reqInwon(), r.procInwon(), r.unprocInwon(),
                r.regInwon(), price, rate, r.discount(), r.memo());
    }

    /**
     * 할인액. 0이면 null로 바꿔 넘긴다 — {@link Amounts}는 "할인액이 있으면 공급률 대신"이라는
     * 규칙이라 0을 그대로 주면 "할인 0원 적용"과 "할인 없음"이 구분되지 않는다.
     */
    private static Integer discountOrNull(DuffSalesRow row) {
        return (row.discount() > 0) ? row.discount() : null;
    }

    private static DuffImportResponse.Line line(DuffSalesRow r, Charge c, long unit, long total,
                                                String result, String salesNo) {
        return line(r, c, unit, total, result, null, salesNo);
    }

    private static DuffImportResponse.Line line(DuffSalesRow r, Charge c, long unit, long total,
                                                String result, String reason, String salesNo) {
        return new DuffImportResponse.Line(
                r.reqCd(), r.reqDate(), r.machulCode(), r.custCode(), r.custName(),
                r.schoolCode(), r.schoolName(), r.grade(),
                r.prodCode(), r.prodName(), r.dtlCode(), r.dtlName(),
                r.procType(), r.chargeType(),
                r.reqInwon(), r.procInwon(), r.unprocInwon(), r.regInwon(),
                c.inwon(), c.basis(),
                r.price(), r.supplyRate(), r.discount(), unit, total, result, reason, salesNo);
    }
}
