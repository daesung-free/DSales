package com.daesung.sales.logistics.service;

import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.LogisCostRate;
import com.daesung.sales.logistics.dto.LogisCostBulkRequest;
import com.daesung.sales.logistics.dto.LogisCostBulkResult;
import java.util.ArrayList;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

/**
 * 물류단가 일괄 수정. 근거: 정본 구분값정리 [10.물류비용등록] "선택 항목 일괄 수정(신규)".
 *
 * <p>레거시에도 다건 일괄수정은 있었다(물류비용등록.vb:525). <b>새로 넣는 것은 작업구분 혼합 차단</b>이다 —
 * 작업구분(반별봉투/개별봉투/개별봉투SET)에 따라 단가 구성이 달라, 서로 다른 작업구분을 한 번에
 * 같은 값으로 덮으면 물류비가 통째로 틀어진다. 레거시엔 그 검증이 없었다.
 */
@Service
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class LogisCostBulkService {

    private final DsreGateway dsreGateway;

    public LogisCostBulkResult bulkUpdate(LogisCostBulkRequest req) {
        if (req.hasNoField()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "변경할 항목을 하나 이상 지정해야 합니다(시험지·OMR·단행본·라벨·기본작업비·출고비·작업구분).");
        }
        List<Integer> targets = req.dtlCds().stream().distinct().toList();

        // 1단계: 대상 전부를 먼저 읽어 검증한다. 한 건이라도 문제면 아무것도 바꾸지 않는다 —
        //        중간까지 반영된 채 실패하면 어디까지 바뀌었는지 담당자가 알 수 없다.
        List<LogisCostRate> found = new ArrayList<>();
        List<Integer> notFound = new ArrayList<>();
        for (Integer dtlCd : targets) {
            dsreGateway.findLogisCost(dtlCd).ifPresentOrElse(found::add, () -> notFound.add(dtlCd));
        }
        assertSamePackType(found);

        // 2단계: 반영. 지정하지 않은 항목은 기존 값을 그대로 쓴다(빈 칸이 0으로 덮이면 단가가 0이 된다).
        int changed = 0;
        int unchanged = 0;
        for (LogisCostRate cur : found) {
            int paper = or(req.paper(), cur.paper());
            int omr = or(req.omr(), cur.omr());
            int etc = or(req.etc(), cur.etc());
            int label = or(req.label(), cur.label());
            int basic = or(req.basic(), cur.basic());
            int trade = or(req.trade(), cur.trade());
            int packtype = or(req.packtype(), cur.packtype());

            if (paper == cur.paper() && omr == cur.omr() && etc == cur.etc() && label == cur.label()
                    && basic == cur.basic() && trade == cur.trade() && packtype == cur.packtype()) {
                unchanged++;
                continue;
            }
            dsreGateway.upsertLogisCost(cur.dtlCd(), paper, omr, etc, label, basic, trade,
                    packtype, cur.bSpare());
            changed++;
        }
        return new LogisCostBulkResult(req.dtlCds().size(), changed, unchanged, notFound);
    }

    /**
     * 선택한 행들의 작업구분이 섞여 있으면 거부한다.
     *
     * <p>작업구분마다 단가 구성이 다르다(반별봉투는 기본작업비·출고비가 0이고, 개별봉투는 값이 있다).
     * 섞인 채 같은 값을 덮으면 한쪽 작업구분의 물류비가 조용히 틀어진다.
     * 작업구분 자체를 바꾸려는 요청이면 대상이 이미 한 종류여야 의도가 분명하다.
     */
    private static void assertSamePackType(List<LogisCostRate> rows) {
        List<Integer> kinds = rows.stream().map(LogisCostRate::packtype).distinct().sorted().toList();
        if (kinds.size() > 1) {
            throw new BusinessException(ErrorCode.INVALID_INPUT,
                    "작업구분이 다른 항목을 함께 수정할 수 없습니다. 선택된 작업구분: " + kinds
                            + " — 작업구분마다 단가 구성이 달라 한 번에 같은 값을 적용하면 물류비가 틀어집니다.");
        }
    }

    private static int or(Integer incoming, int current) {
        return (incoming == null) ? current : incoming;
    }
}
