package com.daesung.sales.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.daesung.sales.audit.entity.StatusEntityType;
import com.daesung.sales.audit.service.StatusHistoryService;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.dsre.gateway.DsreGateway;
import com.daesung.sales.dsre.gateway.DsreOrderRow;
import com.daesung.sales.order.service.OrderService;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * 거래명세서 발급 → 발송준비중 전환 규칙 회귀 고정.
 *
 * <p>DSRE2는 <b>남의 운영 DB</b>다. 잘못 쓰면 우리 화면이 아니라 본사 물류 업무가 틀어지므로,
 * "언제 쓰고 언제 안 쓰는지"를 통합테스트(DSRE off라 안 돈다) 대신 여기서 못박는다.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("거래명세서 발급 전환")
class OrderServiceTest {

    @Mock private DsreGateway dsreGateway;
    @Mock private StatusHistoryService statusHistoryService;
    @InjectMocks private OrderService orderService;

    private static DsreOrderRow order(String stateCode) {
        return new DsreOrderRow(78331, "20260720", stateCode, "", "00001", "거래처", "거래처(주)", "서울",
                "00001", "학교", "상품", "1회", "3", 100, 4, "처리", null, null, null, null,
                1200L, 4, null);
    }

    @Test
    @DisplayName("상품준비중이면 발송준비중으로 바꾸고 이력을 남긴다")
    void 전환_성공() {
        when(dsreGateway.findOrder(78331)).thenReturn(Optional.of(order("S")));
        when(dsreGateway.markReadyToShip(78331)).thenReturn(1);

        var r = orderService.issueStatement(78331);

        assertThat(r.changed()).isTrue();
        assertThat(r.stateCode()).isEqualTo("W");
        assertThat(r.stateName()).isEqualTo("발송준비중");
        // DSRE2엔 이력 테이블이 없다 — 우리가 바꾼 건은 반드시 우리 쪽에 남아야 소명이 된다.
        verify(statusHistoryService).record(eq(StatusEntityType.DSRE_ORDER), eq(78331L), eq("STATE"),
                eq("S"), eq("W"), anyString());
    }

    @Test
    @DisplayName("이미 발송완료면 되돌리지 않는다 — 명세서 재출력은 오류가 아니다")
    void 재출력은_되돌리지_않는다() {
        // 조건부 UPDATE라 DSRE2에서 0행이 바뀐다. 여기서 예외를 던지면
        // 명세서를 두 번 뽑는 정상 업무가 실패로 보인다.
        when(dsreGateway.findOrder(78331)).thenReturn(Optional.of(order("D")));
        when(dsreGateway.markReadyToShip(78331)).thenReturn(0);

        var r = orderService.issueStatement(78331);

        assertThat(r.changed()).isFalse();
        assertThat(r.stateName()).as("바뀌지 않았으므로 현재 상태를 그대로 알려준다").isEqualTo("발송완료");
        verify(statusHistoryService, never())
                .record(any(), anyLong(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("DSRE2에 없는 주문이면 404 — 쓰기를 시도하지 않는다")
    void 없는_주문() {
        when(dsreGateway.findOrder(99999999)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.issueStatement(99999999))
                .isInstanceOf(BusinessException.class);

        verify(dsreGateway, never()).markReadyToShip(anyInt());
    }
}
