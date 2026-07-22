package com.daesung.sales.closing.service;

import com.daesung.sales.closing.config.SupplierProperties;
import com.daesung.sales.closing.dto.TaxInvoiceResponse;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.partner.entity.Partner;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Component;

/**
 * 계산서 → 홈택스 대량발행 양식 xlsx. 근거: 레거시 세금계산신고.vb verbatim.
 * 면세('05') 54컬럼 / 과세('01') 59컬럼(세액 추가). 사업자번호·일자·코드는 텍스트(앞자리0 보존).
 * 품목 4개 초과 시 계산서 분할. ⚠️ 컬럼 위치는 배포 전 실제 홈택스 템플릿 1:1 대조 필요.
 */
@Component
public class TaxInvoiceExcelExporter {

    private static final DateTimeFormatter YYYYMMDD = DateTimeFormatter.BASIC_ISO_DATE;
    private static final int ITEM_SLOTS = 4;

    private static final List<String> SUPPLIER_H = List.of(
            "공급자사업자번호", "공급자종사업장", "공급자상호", "공급자성명", "공급자주소", "공급자업태", "공급자종목", "공급자이메일");
    private static final List<String> BUYER_H = List.of(
            "공급받는자사업자번호", "공급받는자종사업장", "공급받는자상호", "공급받는자성명", "공급받는자주소",
            "공급받는자업태", "공급받는자종목", "공급받는자이메일1", "공급받는자이메일2");

    public byte[] export(TaxInvoiceResponse data, Map<Long, Partner> partners, SupplierProperties supplier) {
        try (Workbook wb = new XSSFWorkbook(); ByteArrayOutputStream bos = new ByteArrayOutputStream()) {
            Sheet free = wb.createSheet("면세(05)");
            Sheet tax = wb.createSheet("과세(01)");
            writeRow(free, 0, header(false));
            writeRow(tax, 0, header(true));

            String issueDate = data.issueDate().format(YYYYMMDD);
            int[] rowIdx = {1, 1}; // [free, tax]

            for (TaxInvoiceResponse.Invoice inv : data.invoices()) {
                boolean taxable = "TAXABLE".equals(inv.taxType());
                Sheet sheet = taxable ? tax : free;
                int si = taxable ? 1 : 0;
                Partner buyer = partners.get(inv.partnerId());
                List<TaxInvoiceResponse.Item> items = inv.items();

                // 품목 4개 초과 시 계산서 분할(각 분할이 별도 계산서 = 청크 합계).
                for (int i = 0; i < items.size(); i += ITEM_SLOTS) {
                    List<TaxInvoiceResponse.Item> chunk = items.subList(i, Math.min(i + ITEM_SLOTS, items.size()));
                    writeRow(sheet, rowIdx[si]++, invoiceRow(taxable, issueDate, supplier, inv, buyer, chunk));
                }
            }

            wb.write(bos);
            return bos.toByteArray();
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.INTERNAL_ERROR, "홈택스 파일 생성 실패: " + e.getMessage());
        }
    }

    /** 헤더 행. */
    private List<String> header(boolean taxable) {
        List<String> h = new ArrayList<>();
        h.add("전자(세금)계산서종류");
        h.add("작성일자");
        h.addAll(SUPPLIER_H);
        h.addAll(BUYER_H);
        h.add("공급가액");
        if (taxable) {
            h.add("세액");
        }
        h.add("비고");
        for (int n = 1; n <= ITEM_SLOTS; n++) {
            h.add("일자" + n);
            h.add("품목" + n);
            h.add("규격" + n);
            h.add("수량" + n);
            h.add("단가" + n);
            h.add("공급가액" + n);
            if (taxable) {
                h.add("세액" + n);
            }
            h.add("비고" + n);
        }
        h.addAll(List.of("현금", "수표", "어음", "외상미수금", "영수청구"));
        return List.copyOf(h);
    }

    /** 계산서 한 행(품목 청크 ≤4). 문자열=텍스트(앞자리0 보존), 금액=숫자. */
    private List<Object> invoiceRow(boolean taxable, String issueDate, SupplierProperties sup,
                                    TaxInvoiceResponse.Invoice inv, Partner buyer,
                                    List<TaxInvoiceResponse.Item> chunk) {
        long supplySum = chunk.stream().mapToLong(TaxInvoiceResponse.Item::supply).sum();
        long taxSum = chunk.stream().mapToLong(TaxInvoiceResponse.Item::tax).sum();
        String day = issueDate.substring(6, 8); // dd

        List<Object> r = new ArrayList<>();
        r.add(taxable ? "01" : "05");
        r.add(issueDate);
        // 공급자(설정)
        r.add(stripHyphen(sup.bizNo())); r.add(""); r.add(nz(sup.name())); r.add(nz(sup.bossName()));
        r.add(nz(sup.addr())); r.add(nz(sup.bizStatus())); r.add(nz(sup.bizItem())); r.add("");
        // 공급받는자(거래처)
        r.add(stripHyphen(buyer != null ? buyer.getBizNo() : inv.partnerBizNo()));
        r.add("");
        r.add(inv.partnerName());
        r.add(buyer != null ? nz(buyer.getBossName()) : nz(inv.partnerBossName()));
        r.add(buyer != null ? joinAddr(buyer.getAddr1(), buyer.getAddr2()) : "");
        r.add(buyer != null ? nz(buyer.getBizStatus()) : "");
        r.add(buyer != null ? nz(buyer.getBizItem()) : "");
        r.add(buyer != null ? nz(buyer.getEmail1()) : "");
        r.add(buyer != null ? nz(buyer.getEmail2()) : "");
        // 합계
        r.add(supplySum);
        if (taxable) {
            r.add(taxSum);
        }
        r.add(""); // 비고
        // 품목 4슬롯(부족분 공란)
        for (int n = 0; n < ITEM_SLOTS; n++) {
            if (n < chunk.size()) {
                TaxInvoiceResponse.Item it = chunk.get(n);
                r.add(day);            // 일자
                r.add(it.name());      // 품목
                r.add("");             // 규격
                r.add("");             // 수량
                r.add("");             // 단가
                r.add(it.supply());    // 공급가액
                if (taxable) {
                    r.add(it.tax());   // 세액
                }
                r.add("");             // 비고
            } else {
                r.add(""); r.add(""); r.add(""); r.add(""); r.add(""); r.add("");
                if (taxable) {
                    r.add("");
                }
                r.add("");
            }
        }
        r.addAll(List.of("", "", "", "", "02")); // 현금/수표/어음/외상/영수청구(청구)
        return r;
    }

    private void writeRow(Sheet sheet, int rowIdx, List<?> values) {
        Row row = sheet.createRow(rowIdx);
        for (int c = 0; c < values.size(); c++) {
            Cell cell = row.createCell(c);
            Object v = values.get(c);
            if (v instanceof Number num) {
                cell.setCellValue(num.doubleValue());
            } else {
                cell.setCellValue(v == null ? "" : v.toString());
            }
        }
    }

    private static String stripHyphen(String s) {
        return (s == null) ? "" : s.replace("-", "");
    }

    private static String joinAddr(String a1, String a2) {
        return (nz(a1) + " " + nz(a2)).trim();
    }

    private static String nz(String s) {
        return (s == null) ? "" : s;
    }
}
