package com.daesung.sales.master;

import static org.assertj.core.api.Assertions.assertThat;

import com.daesung.sales.dsre.gateway.ClientRefRow;
import com.daesung.sales.partner.dto.ClientSyncResult;
import com.daesung.sales.partner.entity.Partner;
import com.daesung.sales.partner.repository.PartnerRepository;
import com.daesung.sales.partner.service.PartnerService;
import com.daesung.sales.support.IntegrationTestSupport;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * 거래처 DSRE2 동기화의 <b>병합 규칙</b>을 고정한다.
 *
 * <p>★DSRE 연결 없이 {@code merge}를 직접 불러 검증한다 — 규칙이 중요한 것이지
 * JDBC가 도는지가 중요한 게 아니다(학교 동기화 테스트와 같은 방식).
 *
 * <p>‼️여기서 지키려는 것은 <b>덮어쓰지 않는 것</b>이다. 담보·거래처구분이 동기화 한 번에
 * 날아가면 채권 화면이 통째로 비고, 담당자는 다음부터 이 버튼을 안 누른다.
 */
@DisplayName("거래처 DSRE2 동기화 — 보존형 병합")
class ClientSyncMergeTest extends IntegrationTestSupport {

    private static final String SFX = "-CS" + (System.nanoTime() % 1_000_000L);

    @Autowired
    private PartnerService partnerService;
    @Autowired
    private PartnerRepository partnerRepository;

    private ClientRefRow row(String code, String name, String bizNo, boolean expired) {
        return new ClientRefRow(code, name + "상사", name, "부산", "부산관할", bizNo, "김대표",
                "도소매", "서적", "051-1", "051-2", "010-1", "051-9",
                "a@b.com", null, "12345", "부산시 어딘가", 65, expired);
    }

    @Test
    @DisplayName("★담보·거래처구분은 동기화가 덮어쓰지 않는다 — 우리가 넣은 값이다")
    void 수기값_보존() {
        String code = "CSK" + SFX;
        // 우리 쪽에서 담보를 넣어 둔 거래처
        long id = createId("/masters/clients", java.util.Map.of(
                "code", code, "name", "기존거래처", "type", "NORMAL",
                "assureAmount", 5_000_000, "assureExpiry", "2027-01-01",
                "assureNote", "부동산", "clientCategory", "특약점"));

        ClientSyncResult r = partnerService.merge(List.of(row(code, "동기화된이름", "111-11-11111", false)));

        assertThat(r.updated()).isEqualTo(1);
        assertThat(r.added()).isZero();

        Partner p = partnerRepository.findById(id).orElseThrow();
        // DSRE 관리 항목은 덮어쓴다
        assertThat(p.getName()).isEqualTo("동기화된이름");
        assertThat(p.getBizNo()).isEqualTo("111-11-11111");
        assertThat(p.getCityName()).isEqualTo("부산");
        // ★우리 값은 그대로
        assertThat(p.getAssureAmount()).as("담보금액이 날아가면 안 된다").isEqualTo(5_000_000L);
        assertThat(p.getAssureNote()).isEqualTo("부동산");
        assertThat(p.getClientCategory()).isEqualTo("특약점");
    }

    @Test
    @DisplayName("없는 거래처는 새로 만들고, DSRE2에 없는 우리 거래처는 손대지 않는다")
    void 신규추가_그리고_우리것은_유지() {
        String mine = "CSM" + SFX;
        createId("/masters/clients", java.util.Map.of("code", mine, "name", "우리만아는거래처", "type", "NORMAL"));

        String fresh = "CSN" + SFX;
        ClientSyncResult r = partnerService.merge(List.of(row(fresh, "새거래처", "222-22-22222", false)));

        assertThat(r.added()).isEqualTo(1);
        assertThat(partnerRepository.findByCode(fresh)).isPresent();
        // ★DSRE2에 없다고 지우거나 만료시키지 않는다 — 과거 매출이 FK로 물려 있다
        Partner ours = partnerRepository.findByCode(mine).orElseThrow();
        assertThat(ours.getEndDate()).isNull();
        assertThat(r.keptLocal()).isPositive();
    }

    @Test
    @DisplayName("만료지사(END_GUBUN='Y')는 종료일이 찍히고, 풀리면 다시 지워진다")
    void 만료지사() {
        String code = "CSE" + SFX;
        partnerService.merge(List.of(row(code, "만료지사", "333-33-33333", true)));
        Partner p = partnerRepository.findByCode(code).orElseThrow();
        assertThat(p.getEndDate()).isNotNull().isBeforeOrEqualTo(LocalDate.now());

        partnerService.merge(List.of(row(code, "만료지사", "333-33-33333", false)));
        assertThat(partnerRepository.findByCode(code).orElseThrow().getEndDate())
                .as("원본에서 만료가 풀리면 우리도 풀어야 한다").isNull();
    }

    @Test
    @DisplayName("‼️원본 값이 비어 있으면 지우지 않고 기존 값을 지킨다")
    void 빈값은_덮어쓰지_않는다() {
        String code = "CSB" + SFX;
        partnerService.merge(List.of(row(code, "채워진거래처", "444-44-44444", false)));

        ClientRefRow blank = new ClientRefRow(code, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, null, false);
        partnerService.merge(List.of(blank));

        Partner p = partnerRepository.findByCode(code).orElseThrow();
        assertThat(p.getName()).as("이름이 null 로 덮여 사라지면 안 된다").isEqualTo("채워진거래처");
        assertThat(p.getBizNo()).isEqualTo("444-44-44444");
    }
}
