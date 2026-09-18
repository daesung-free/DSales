package com.daesung.sales.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.audit.CurrentAuditor;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.NewOrder;
import com.daesung.sales.order.dto.OrderCreateRequest;
import com.daesung.sales.order.dto.OrderCreateResponse;
import com.daesung.sales.order.service.OrderService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * 신규 주문 등록의 <b>검증 규칙</b>. 근거: 레거시 특약점 사이트 {@code Application_SQL.xml:266~299}.
 *
 * <p>★통합테스트가 아니라 단위테스트인 이유 — 주문 등록은 DSRE2 연동 경로라
 * {@code daesung.dsre.enabled=false}인 테스트 환경에서는 <b>빈 자체가 만들어지지 않는다</b>.
 * DB를 상대하는 부분은 복제본이 붙은 환경에서 확인하고, 여기서는 게이트웨이를 가짜로 두고
 * <b>"무엇을 거부하는가"</b>만 고정한다.
 *
 * <p>★<b>왜 거부가 중요한가</b> — 인원도 수량도 없는 주문이 서면 물류가 무엇을 몇 개
 * 보낼지 알 수 없다. 조용히 통과시키면 그 주문은 발송 단계에서야 문제가 드러난다.
 */
@DisplayName("신규 주문 등록 — 검증 규칙")
class OrderCreateValidationTest {

    private DsreGateway gateway;
    private OrderService service;

    @BeforeEach
    void setUp() {
        gateway = mock(DsreGateway.class);
        StatusHistoryService history = mock(StatusHistoryService.class);
        CurrentAuditor auditor = mock(CurrentAuditor.class);
        when(auditor.username()).thenReturn("tester");
        when(gateway.createOrder(any(), anyString())).thenReturn(777);
        service = new OrderService(gateway, history, auditor, mock(com.daesung.sales.sale.repository.SaleRepository.class));
    }

    private OrderCreateRequest req(OrderCreateRequest.ClassLine... classes) {
        return new OrderCreateRequest(78331, "70501", "10001", null, null, null,
                "김선생", "010-0000-0000", null, null, null, null, null, List.of(classes));
    }

    /** 간편신청 — 인원만 채운 반. */
    private OrderCreateRequest.ClassLine easy(String name, Integer h, Integer s, Integer c) {
        return new OrderCreateRequest.ClassLine(name, null, h, s, c, null);
    }

    /** 과목신청 — 과목별 수량을 채운 반. */
    private OrderCreateRequest.ClassLine bySubject(String name, int resCd, int qty) {
        return new OrderCreateRequest.ClassLine(name, null, null, null, null,
                List.of(new OrderCreateRequest.SubjectQty(resCd, qty)));
    }

    @Test
    @DisplayName("★간편신청 — 인원 합이 총수량이 되고 방식은 S로 잡힌다")
    void 간편신청() {
        OrderCreateResponse r = service.create(req(easy("3학년 1반", 15, 12, 3)));

        assertThat(r.reqCd()).isEqualTo(777);
        assertThat(r.stateCode()).as("등록 직후는 항상 접수완료").isEqualTo("A");
        assertThat(r.totalQty()).as("15+12+3").isEqualTo(30);
        assertThat(r.subjectLineCount()).as("과목 줄은 없다").isZero();

        ArgumentCaptor<NewOrder> sent = ArgumentCaptor.forClass(NewOrder.class);
        org.mockito.Mockito.verify(gateway).createOrder(sent.capture(), anyString());
        assertThat(sent.getValue().classes().get(0).applyType()).isEqualTo("S");
    }

    @Test
    @DisplayName("★과목신청 — 수량 합이 총수량이 되고 방식은 N으로 잡힌다")
    void 과목신청() {
        OrderCreateResponse r = service.create(req(
                bySubject("3학년 1반", 101, 30), bySubject("3학년 2반", 101, 28)));

        assertThat(r.classCount()).isEqualTo(2);
        assertThat(r.subjectLineCount()).isEqualTo(2);
        assertThat(r.totalQty()).isEqualTo(58);

        ArgumentCaptor<NewOrder> sent = ArgumentCaptor.forClass(NewOrder.class);
        org.mockito.Mockito.verify(gateway).createOrder(sent.capture(), anyString());
        assertThat(sent.getValue().classes().get(0).applyType()).isEqualTo("N");
    }

    @Test
    @DisplayName("★간편인데 인원이 0이면 거부 — 물류가 무엇을 보낼지 알 수 없다")
    void 간편_인원없음() {
        assertThatThrownBy(() -> service.create(req(easy("3학년 1반", 0, 0, 0))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("간편신청인데 인원이 없습니다")
                .hasMessageContaining("3학년 1반");
    }

    @Test
    @DisplayName("★과목신청이라고 해놓고 수량이 없으면 거부")
    void 과목_수량없음() {
        OrderCreateRequest.ClassLine c =
                new OrderCreateRequest.ClassLine("3학년 1반", "N", null, null, null, null);

        assertThatThrownBy(() -> service.create(req(c)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("과목 수량이 없습니다");
    }

    @Test
    @DisplayName("몇 번째 반이 잘못됐는지 알려준다 — 반이 여럿이면 찾을 수 없다")
    void 잘못된_반_위치() {
        assertThatThrownBy(() -> service.create(req(
                easy("1반", 10, 0, 0), easy("2반", 5, 0, 0), easy("3반", 0, 0, 0))))
                .hasMessageContaining("3번째 반(3반)");
    }

    @Test
    @DisplayName("★기본값 — procYn 은 비워 넘기고(시행 기본값) · 배송은 H(화물)")
    void 기본값() {
        service.create(req(easy("1반", 10, 0, 0)));

        ArgumentCaptor<NewOrder> sent = ArgumentCaptor.forClass(NewOrder.class);
        org.mockito.Mockito.verify(gateway).createOrder(sent.capture(), anyString());
        NewOrder o = sent.getValue();
        // ★procYn 은 비워서 넘긴다 — 미지정이면 **시행 기본값**을 쓰는 게 스펙이고,
        //   그 값은 DSRE2 시행 마스터에 있다(게이트웨이 SQL 의 COALESCE).
        //   여기서 'N'으로 박았더니 성적처리(Y) 시행이 비처리로 저장됐다(2026-09-18 지적).
        assertThat(o.procYn()).as("서버가 임의로 채우지 않는다").isNull();
        assertThat(o.procYn2()).isEqualTo("N");
        assertThat(o.deliveryGubun()).as("미지정이면 화물").isEqualTo("H");
    }

    @Test
    @DisplayName("★접수완료 건만 삭제된다 — 검수 이후는 물류가 이미 움직였다")
    void 삭제는_접수완료만() {
        when(gateway.findOrder(79069)).thenReturn(java.util.Optional.of(order("G")));

        assertThatThrownBy(() -> service.delete(79069, "테스트 정리"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("접수완료 상태에서만")
                .hasMessageContaining("상품검수");
    }

    @Test
    @DisplayName("★삭제는 행을 지우지 않고 상태를 C(삭제)로 바꾼다")
    void 삭제는_상태전환() {
        when(gateway.findOrder(79069)).thenReturn(java.util.Optional.of(order("A")));
        when(gateway.changeState(79069, "A", "C")).thenReturn(1);

        OrderService.ItemResult r = service.delete(79069, "연동 테스트 정리");

        assertThat(r.changed()).isTrue();
        assertThat(r.fromCode()).isEqualTo("A");
        assertThat(r.toCode()).as("물리삭제가 아니라 C(삭제) 상태").isEqualTo("C");
    }

    @Test
    @DisplayName("그 사이 DSRE2가 먼저 옮겼으면 성공으로 보고하지 않는다")
    void 삭제_경합() {
        when(gateway.findOrder(79069)).thenReturn(java.util.Optional.of(order("A")));
        when(gateway.changeState(79069, "A", "C")).thenReturn(0);

        OrderService.ItemResult r = service.delete(79069, null);

        assertThat(r.changed()).isFalse();
        assertThat(r.message()).contains("다른 곳에서 상태가 바뀌었");
    }

    /** 상태코드만 의미 있는 최소 주문 행. */
    private static com.daesung.sales.dsre.gateway.DsreOrderRow order(String stateCode) {
        return new com.daesung.sales.dsre.gateway.DsreOrderRow(
                79069, "20260918", stateCode, null, null, null, null, null, null, null,
                null, null, null, null, null, null, null, null, null, null, 0L, 0, null, null);
    }
}
