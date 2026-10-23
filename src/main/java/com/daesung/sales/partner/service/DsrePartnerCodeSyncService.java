package com.daesung.sales.partner.service;

import com.daesung.sales.dsre.gateway.BranchRow;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.partner.dto.DsreCodeSyncResult;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 거래처에 <b>DSRE 거래처코드</b>를 채운다.
 *
 * <h3>왜 필요한가</h3>
 * 우리에게 거래처를 가리키는 코드가 하나({@code partners.code} = DSLab 매출코드, 'A10A195' 꼴)뿐인데,
 * <b>학교 마스터와 발주처 양식은 DSRE 코드('10003' 꼴)를 쓴다.</b>
 *
 * <pre>
 *   DSRE tbl_cust_info :  CUST_CD = '10003'   ·  MACHUL_CD = 'A10A195'
 *   우리 partners      :  code    = 'A10A195'
 *   우리 schools       :  cust_code = '10003'          ← 전부 숫자(4,857/4,860)
 * </pre>
 *
 * 두 체계가 달라 {@code schools.cust_code = partners.code} 조인이 <b>한 건도 안 붙었고</b>,
 * 그래서 학교/학원검색의 거래처명1·지역명이 통째로 빈 칸이었다(프론트 실측 2026-10-23 §6③).
 * 발주처가 지정한 PDF 파일명({@code RESULT_10003_강남 하람도서_…}, §3②)도 같은 이유로 못 만들었다.
 *
 * <h3>규율</h3>
 * <ul>
 *   <li>★<b>DSRE 는 읽기만 한다.</b> 쓰는 것은 우리 {@code partners.dsre_code} 하나다.</li>
 *   <li>★<b>지우지 않는다.</b> DSRE 에서 사라진 거래처의 코드도 남겨 둔다 —
 *       지우면 그 거래처의 학교가 다시 빈 칸이 되는데, 사라진 이유를 우리가 모른다.</li>
 *   <li>★<b>중복을 막지 않고 보고한다.</b> 같은 DSRE 코드를 여럿이 쓰면 UNIQUE 로는
 *       동기화 전체가 실패한다. 채우고 목록으로 알린다.</li>
 * </ul>
 *
 * <p>‼️DSRE 연동이 꺼져 있으면 이 빈은 만들어지지 않는다(게이트웨이가 없다).
 */
@Service
@ConditionalOnProperty(name = "daesung.dsre.enabled", havingValue = "true")
@RequiredArgsConstructor
public class DsrePartnerCodeSyncService {

    private final DsreGateway dsreGateway;
    private final PartnerRepository partnerRepository;

    @Transactional
    public DsreCodeSyncResult sync() {
        List<BranchRow> rows = dsreGateway.listBranches(null);

        // 매출코드 → 우리 거래처. 한 번에 읽는다(거래처 수만큼 조회하면 수백 번이다).
        Map<String, Partner> byCode = new HashMap<>();
        partnerRepository.findAll().forEach(p -> {
            if (p.getCode() != null) {
                byCode.putIfAbsent(p.getCode(), p);
            }
        });

        int filled = 0;
        int changed = 0;
        int unchanged = 0;
        int unmatched = 0;
        // 같은 DSRE 코드를 누가 쓰는지 — 순서를 보존해 보고가 매번 같게 나오도록 한다.
        Map<String, Integer> useCount = new LinkedHashMap<>();

        for (BranchRow r : rows) {
            String dsreCode = trimToNull(r.custCode());
            String ourCode = trimToNull(r.machulCode());
            if (dsreCode == null || ourCode == null) {
                continue;   // 짝이 안 맞는 DSRE 행 — 우리가 손댈 게 없다
            }
            Partner p = byCode.get(ourCode);
            if (p == null) {
                unmatched++;
                continue;
            }
            useCount.merge(dsreCode, 1, Integer::sum);

            String before = trimToNull(p.getDsreCode());
            if (before == null) {
                p.applyDsreCode(dsreCode);
                filled++;
            } else if (!before.equals(dsreCode)) {
                p.applyDsreCode(dsreCode);
                changed++;
            } else {
                unchanged++;
            }
        }

        List<String> dups = new ArrayList<>();
        useCount.forEach((code, n) -> {
            if (n > 1) {
                dups.add(code);
            }
        });

        return new DsreCodeSyncResult(rows.size(), filled, changed, unchanged, unmatched, dups);
    }

    private static String trimToNull(String v) {
        if (v == null) {
            return null;
        }
        String t = v.trim();
        return t.isEmpty() ? null : t;
    }
}
