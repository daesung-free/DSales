package com.daesung.sales.inventory.service;

import com.daesung.sales.common.excel.ExcelSheetReader;
import com.daesung.sales.common.excel.ExcelSheetReader.Header;
import com.daesung.sales.common.excel.ExcelSheetReader.RowView;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.inventory.dto.DisposalRequest;
import com.daesung.sales.inventory.dto.DisposalResponse;
import com.daesung.sales.inventory.dto.DisposalUploadResponse;
import com.daesung.sales.product.entity.Product;
import com.daesung.sales.product.repository.ProductRepository;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

/**
 * 폐기수량 <b>일괄 등록</b>(엑셀 업로드).
 *
 * <p>근거: 9/27 회의 항목 8 — "폐기수량 일일이 적어야되는데 <b>일괄 등록 가능하게</b>.
 * 엑셀업로드(<b>수불부 상품코드 + 수량</b>) 적용."
 *
 * <p>양식은 두 칸이면 된다. 열 순서가 달라도, 모르는 열이 붙어 있어도 읽는다
 * ({@link ExcelSheetReader}) — 담당자가 쓰던 파일을 그대로 올릴 수 있어야 한다.
 * <pre>
 *   상품코드 | 수량 | (비고)
 * </pre>
 *
 * <p>★<b>한 건이라도 틀리면 전부 넣지 않는다.</b> 폐기는 재고를 깎는 전표라,
 * 절반만 들어가면 담당자는 무엇이 들어가고 무엇이 빠졌는지 파일과 대조해야 한다.
 * 먼저 전량을 검증해 오류 목록을 돌려주고, 깨끗할 때만 한 전표로 등록한다.
 *
 * <p>‼️사유 칸은 양식에 두지 않는다 — 9/27 A-3(항목 9)에서 폐기 사유 입력이 제거됐다.
 * 비고는 읽어서 넘긴다.
 */
@Service
@RequiredArgsConstructor
public class DisposalUploadService {

    private static final Header H_CODE = Header.of("상품코드", "도서코드", "bookCode", "code");
    private static final Header H_QTY = Header.of("수량", "폐기수량", "qty");
    private static final Header H_MEMO = Header.of("비고", "memo");

    private final ProductRepository productRepository;
    private final InventoryService inventoryService;

    /**
     * @param dryRun true 면 검증만 하고 등록하지 않는다. 담당자가 올리기 전에 파일을 확인할 수 있어야 한다.
     */
    @Transactional
    public DisposalUploadResponse upload(MultipartFile file, LocalDate processedDate,
                                         Long warehouseId, boolean dryRun) {
        // ★dryRun(파일만 읽기)에는 처리일자·창고가 없어도 된다 — 아직 어느 창고에서 뺄지
        //   고르기 전이기 때문이다. 실제 등록에서만 요구한다.
        if (!dryRun && (processedDate == null || warehouseId == null)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "등록하려면 처리일자와 창고가 필요합니다. 파일만 확인하려면 dryRun=true 로 호출하세요.");
        }
        ExcelSheetReader sheet = ExcelSheetReader.read(file, List.of(H_CODE, H_QTY));

        List<DisposalUploadResponse.Line> lines = new ArrayList<>();
        List<DisposalRequest.Item> items = new ArrayList<>();
        // 같은 상품이 여러 줄에 나오면 합친다 — 담당자가 창고별·회차별로 나눠 적는 경우가 있다.
        Map<Long, Integer> mergedQty = new LinkedHashMap<>();
        int failed = 0;

        for (RowView r : sheet.rows()) {
            String code = null;
            try {
                code = r.required(H_CODE);
                int qty = r.intVal(H_QTY);
                if (qty <= 0) {
                    throw new BusinessException(ErrorCode.INVALID_INPUT,
                            "폐기수량은 1 이상이어야 합니다: " + qty);
                }
                Product product = productRepository.findByCode(code)
                        .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND,
                                "도서 마스터에 없는 상품코드입니다"));

                mergedQty.merge(product.getId(), qty, Integer::sum);
                items.add(new DisposalRequest.Item(product.getId(), qty, r.str(H_MEMO), null));
                lines.add(new DisposalUploadResponse.Line(r.rowNo(), "OK", code,
                        product.getName(), qty, null));
            } catch (BusinessException e) {
                lines.add(new DisposalUploadResponse.Line(r.rowNo(), "ERROR", code, null, 0,
                        e.getMessage()));
                failed++;
            }
        }

        // ★오류가 하나라도 있으면 등록하지 않는다. 부분 등록은 되돌리기가 더 어렵다.
        if (failed > 0 || dryRun) {
            return new DisposalUploadResponse(null, lines.size() - failed, failed, null, lines);
        }
        if (items.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "등록할 행이 없습니다.");
        }

        DisposalResponse result = inventoryService.dispose(
                new DisposalRequest(processedDate, warehouseId, items));
        return new DisposalUploadResponse(result.disposalNo(), lines.size(), 0,
                result.warehouseName(), lines);
    }
}
