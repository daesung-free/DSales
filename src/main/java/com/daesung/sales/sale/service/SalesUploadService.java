package com.daesung.sales.sale.service;

import com.daesung.sales.closing.service.PeriodLockService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.money.Amounts;
import com.daesung.sales.common.sequence.SequenceService;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductPartnerPriceRepository;
import com.daesung.sales.product.repository.ProductRepository;
import com.daesung.sales.sale.dto.SalesUploadResponse;
import com.daesung.sales.sale.entity.Sale;
import com.daesung.sales.sale.repository.SaleRepository;
import com.daesung.sales.salestype.entity.SalesCategory;
import com.daesung.sales.salestype.entity.ShipmentType;
import java.io.IOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 매출 엑셀 업로드(발주처 요청 추가1). 근거: 레거시 DsSales 매출가져오기.vb(salesData 일괄 INSERT, 재고 미반영).
 * 표준양식(매출 업로드양식_샘플.xlsx, 12컬럼) 파싱 → 매출 원장 일괄 등록. 재고는 건드리지 않음.
 *
 * 컬럼(0-base): 0 거래일자 · 1 거래처코드 · 2 학교코드 · 3 분류코드 · 4 도서코드 · 5 회차
 *              · 6 정가(선택) · 7 공급률(%) · 8 수량 · 9 금액(참고) · 10 구분(출고유형) · 11 메모
 * 규칙: 상품=분류코드+도서코드 조합 / 정가·공급률 미입력 시 도서마스터·거래처단가 자동조회 /
 *      공급률 0.75 소수는 ×100(=75) 정규화 / 위탁출고·취소·반품은 거부(전용 메뉴) / 금액은 Amounts로 재계산.
 */
@Service
@RequiredArgsConstructor
public class SalesUploadService {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;

    private final SaleRepository saleRepository;
    private final PartnerRepository partnerRepository;
    private final ProductRepository productRepository;
    private final ProductPartnerPriceRepository partnerPriceRepository;
    private final SequenceService sequenceService;
    private final PeriodLockService periodLockService;

    private record Kind(ShipmentType shipmentType, SalesCategory category) {}

    /** 구분(한글) → 출고유형/회계. 위탁출고·취소·반품은 null(업로드 거부 — 레거시 매출가져오기도 출고/매출/무상만). */
    private static Kind mapKind(String gubun) {
        if (gubun == null) {
            return null;
        }
        return switch (gubun.trim()) {
            case "정상출고", "출고", "매출" -> new Kind(ShipmentType.NORMAL_SHIP, SalesCategory.SALE);
            case "증정용", "증정", "무상" -> new Kind(ShipmentType.GIFT, SalesCategory.FREE);
            case "교사용" -> new Kind(ShipmentType.TEACHER_USE, SalesCategory.FREE);
            default -> null;   // 위탁출고/취소/반품 등
        };
    }

    @Transactional
    public SalesUploadResponse upload(MultipartFile file) {
        List<SalesUploadResponse.Line> lines = new ArrayList<>();
        int imported = 0;
        int failed = 0;

        try (Workbook wb = WorkbookFactory.create(file.getInputStream())) {
            Sheet sheet = wb.getSheetAt(0);
            for (int r = 1; r <= sheet.getLastRowNum(); r++) {   // 0행=헤더
                Row row = sheet.getRow(r);
                if (row == null) {
                    continue;
                }
                LocalDate date = parseDate(row.getCell(0));
                if (date == null) {
                    continue;   // 거래일자가 날짜가 아니면 설명행/빈행 → 스킵
                }
                int rowNo = r + 1;
                String custCode = str(row, 1);
                String productCode = safe(str(row, 3)) + safe(str(row, 4));   // 분류+도서 조합
                int qty = intVal(row, 8);
                try {
                    Kind kind = mapKind(str(row, 10));
                    if (kind == null) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT,
                                "업로드 불가 구분: " + str(row, 10) + " (정상출고/증정용/교사용만 허용 — 위탁·취소·반품은 전용 메뉴)");
                    }
                    if (qty <= 0) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT, "수량이 0 이하");
                    }
                    periodLockService.assertNotLocked(date);

                    Partner partner = partnerRepository.findByCode(custCode)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "거래처 없음: " + custCode));
                    Product product = productRepository.findByCode(productCode)
                            .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "도서 없음(분류+도서): " + productCode));

                    Integer unitPrice = intOrNull(row, 6);
                    if (unitPrice == null) {
                        unitPrice = product.getPrice();
                    }
                    Integer supplyRate = rate(row, 7);
                    if (supplyRate == null) {
                        supplyRate = partnerPriceRepository
                                .findByProductIdAndPartnerId(product.getId(), partner.getId())
                                .map(m -> m.getSupplyRate()).orElse(null);
                    }
                    if (unitPrice == null || supplyRate == null) {
                        throw new BusinessException(ErrorCode.INVALID_INPUT,
                                "정가·공급률이 없고 거래처별 단가 매핑도 없음");
                    }

                    // 표준 업로드 양식(12컬럼)에 세액 항목이 없다 → 0. 세액이 필요한 건은 등록 후 수정한다.
                    Amounts amt = Amounts.of(unitPrice, supplyRate, qty, product.isTaxFree());
                    String salesNo = "I-" + date.format(YYYYMMDD) + "-" + sequenceService.next(SequenceService.SEQ_INVOICE);
                    Sale sale = Sale.createBulk(salesNo, date, partner, product,
                            kind.shipmentType(), kind.category(), unitPrice, supplyRate, qty,
                            amt.supplyAmount(), amt.tax(), amt.totalAmount(), str(row, 11), null);
                    sale.applyUploadDetail(str(row, 2), null, intOrNull(row, 5));   // 학교코드·(학교명 미구현)·회차
                    saleRepository.save(sale);

                    imported++;
                    lines.add(new SalesUploadResponse.Line(rowNo, "IMPORTED", salesNo,
                            custCode, productCode, qty, amt.supplyAmount(), null));
                } catch (BusinessException e) {
                    failed++;
                    lines.add(new SalesUploadResponse.Line(rowNo, "ERROR", null,
                            custCode, productCode, qty, 0, e.getMessage()));
                }
            }
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "엑셀 파일을 읽을 수 없습니다: " + e.getMessage());
        }
        return new SalesUploadResponse(imported, failed, lines);
    }

    // ── POI 셀 헬퍼 ──
    private static String safe(String s) {
        return (s == null) ? "" : s;
    }

    private static String str(Row row, int i) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        return switch (c.getCellType()) {
            case STRING -> {
                String v = c.getStringCellValue().trim();
                yield v.isEmpty() ? null : v;
            }
            case NUMERIC -> {
                double d = c.getNumericCellValue();
                yield (d == Math.floor(d)) ? String.valueOf((long) d) : String.valueOf(d);
            }
            default -> null;
        };
    }

    private static LocalDate parseDate(Cell c) {
        if (c == null) {
            return null;
        }
        try {
            if (c.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(c)) {
                return c.getLocalDateTimeCellValue().toLocalDate();
            }
            String s = (c.getCellType() == CellType.STRING) ? c.getStringCellValue().trim() : null;
            return (s == null || s.isEmpty()) ? null : LocalDate.parse(s);   // yyyy-MM-dd
        } catch (Exception e) {
            return null;
        }
    }

    private static Double dbl(Row row, int i) {
        Cell c = row.getCell(i);
        if (c == null) {
            return null;
        }
        try {
            return switch (c.getCellType()) {
                case NUMERIC -> c.getNumericCellValue();
                case STRING -> {
                    String v = c.getStringCellValue().trim().replace(",", "");
                    yield v.isEmpty() ? null : Double.parseDouble(v);
                }
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer intOrNull(Row row, int i) {
        Double d = dbl(row, i);
        return (d == null) ? null : (int) Math.round(d);
    }

    private static int intVal(Row row, int i) {
        Integer v = intOrNull(row, i);
        return (v == null) ? 0 : v;
    }

    /** 공급률: 0.75 소수는 ×100(=75), 75 등 1 이상은 그대로. 정수 반올림. */
    private static Integer rate(Row row, int i) {
        Double d = dbl(row, i);
        if (d == null) {
            return null;
        }
        return (int) Math.round(d <= 1.0 ? d * 100 : d);
    }
}
