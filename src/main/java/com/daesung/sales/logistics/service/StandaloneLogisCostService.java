package com.daesung.sales.logistics.service;

import com.daesung.sales.dsre.gateway.LogisCostDetailRow;
import com.daesung.sales.logistics.entity.ProductLogisRate;
import com.daesung.sales.logistics.repository.ProductLogisRateRepository;
import com.daesung.sales.material.entity.MaterialBom;
import com.daesung.sales.material.repository.MaterialBomRepository;
import com.daesung.sales.product.entity.MaterialType;
import com.daesung.sales.sale.repository.SaleRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * <b>단독 상품</b>(수불부노출 Y)의 물류 작업비.
 *
 * <p>근거: 발주처 확정(2026-10-23 #10) — "상품별 출처 구분, 기준=도서관리 수불부노출.
 * N(DSRE병행)→DSRE 인원 값. <b>Y(단독)→'인원' 자리에 매출프로그램 출고수량</b>.
 * 병행 상품 두 값 합산 시 이중계산 주의. 계산식·단가 기존 그대로."
 *
 * <h3>계산식은 레거시 그대로다</h3>
 * <pre>
 *   자재비 = Σ(자재별 소요수량 × 출고수량 × 그 자재구분 단가)
 *   노무비 = 출고수량 × (기본작업비 + 출고비)
 * </pre>
 * 다른 것은 <b>'인원' 자리에 무엇이 들어가느냐</b> 하나뿐이다 —
 * DSRE 병행은 신청 인원, 단독은 우리 출고수량.
 *
 * <h3>자재구분 → 단가 (도서관리·제품수불부현황 구조보완 요청안 2026-08-31 §2-1)</h3>
 * <pre>
 *   시험지 → 시험지   OMR → OMR   단행본·책자 → 단행본   라벨 → 라벨
 *   해설지 → **시험지**   ← 별도 해설지 단가는 만들지 않는다(문서 명시)
 * </pre>
 *
 * <h3>‼️이중계산 방지</h3>
 * 병행(N) 상품은 DSRE 쪽에서 이미 집계돼 온다. 여기서는 <b>단독(Y)만</b> 센다 —
 * 한 상품이 양쪽에 다 잡히면 금액이 두 배가 된다. 발주처가 콕 집어 경고한 지점이다.
 *
 * <h3>단가가 없으면 행을 만들지 않는다</h3>
 * 단가 미등록 상품을 0원으로 끼워 넣으면 "물류비가 0인 출고"로 보여 그 사실이 묻힌다.
 * 등록이 필요한 상품은 물류비용등록 화면에서 담당자가 채운다.
 */
@Service
@RequiredArgsConstructor
public class StandaloneLogisCostService {

    private final SaleRepository saleRepository;
    private final ProductLogisRateRepository rateRepository;
    private final MaterialBomRepository materialBomRepository;

    /** 단독 상품 행. 비어 있으면 더할 것이 없다는 뜻이다. */
    @Transactional(readOnly = true)
    public List<LogisCostDetailRow> rows(LocalDate from, LocalDate to) {
        List<Object[]> shipped = saleRepository.standaloneShippedQty(from, to);
        if (shipped.isEmpty()) {
            return List.of();
        }

        List<LogisCostDetailRow> out = new ArrayList<>();
        for (Object[] r : shipped) {
            Long productId = ((Number) r[0]).longValue();
            String productCode = (String) r[1];
            String productName = (String) r[2];
            String grade = (String) r[3];
            String partnerCode = (String) r[4];
            String partnerName = (String) r[5];
            long qty = ((Number) r[6]).longValue();

            ProductLogisRate rate = rateRepository.findByProductId(productId).orElse(null);
            if (rate == null) {
                continue;   // 단가 미등록 — 0원 행으로 끼워 넣지 않는다(위 주석 참고)
            }

            // 자재 소요수량을 자재구분별로 모은다. 해설지는 시험지 쪽에 합친다.
            long paperQty = 0;
            long omrQty = 0;
            long etcQty = 0;
            long labelQty = 0;
            for (MaterialBom b : materialBomRepository.findBySet(productId)) {
                long need = (long) b.getQtyPerSet() * qty;
                switch (b.getMaterial().getMaterialType()) {
                    case EXAM_PAPER, ANSWER_SHEET -> paperQty += need;   // 해설지→시험지 단가
                    case OMR -> omrQty += need;
                    case LABEL -> labelQty += need;
                    default -> etcQty += need;                           // 단행본·기타
                }
            }

            long paperAmt = paperQty * rate.getPaper();
            long omrAmt = omrQty * rate.getOmr();
            // 라벨은 레거시 상세에도 별도 칸이 없어 '기타'에 합친다(금액은 라벨 단가로 계산).
            long etcAmt = etcQty * rate.getEtc() + labelQty * rate.getLabel();
            long basicAmt = qty * rate.getBasic();
            long tradeAmt = qty * rate.getTrade();

            out.add(new LogisCostDetailRow(
                    null,                   // 신청일 — 기간 합계라 대표 날짜가 없다(DSRE 신청 축 없음)
                    0,                      // 신청번호 — 단독은 DSRE 신청이 없다
                    productCode, productName, grade,
                    0, "(단독)",             // 시행코드·시행명 — DSRE 축이 없다
                    partnerCode, partnerName,
                    null, null, null,       // 지역·도시·학교 — 거래처 축으로만 집계한다
                    paperQty + omrQty + etcQty + labelQty,
                    paperQty, paperAmt, omrQty, omrAmt, etcQty + labelQty, etcAmt,
                    (int) qty,              // ★'인원' 자리에 출고수량
                    basicAmt, tradeAmt,
                    paperAmt + omrAmt + etcAmt + basicAmt + tradeAmt,
                    "S",                    // 구분 — 단독은 사고(A)가 없다
                    false,                  // 취소 — 단독은 DSRE 발송취소 축이 없다
                    false));                // 자동계산분이다(수기 등록 아님)
        }
        return out;
    }

    /** 단가가 없어 빠진 상품. 화면이 "등록이 필요하다"고 알릴 수 있게 따로 낸다. */
    @Transactional(readOnly = true)
    public List<String> missingRateProducts(LocalDate from, LocalDate to) {
        Map<String, Boolean> seen = new HashMap<>();
        List<String> out = new ArrayList<>();
        for (Object[] r : saleRepository.standaloneShippedQty(from, to)) {
            Long productId = ((Number) r[0]).longValue();
            String code = (String) r[1];
            if (seen.putIfAbsent(code, true) == null
                    && rateRepository.findByProductId(productId).isEmpty()) {
                out.add(code);
            }
        }
        return out;
    }
}
