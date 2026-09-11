package com.daesung.sales.inventory.service;

import com.daesung.sales.inventory.dto.StockWarning;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

/**
 * 한 요청 동안 쌓인 재고 경고를 모은다.
 *
 * <p>★<b>왜 수집기인가.</b> 경고는 재고를 실제로 깎는 가장 안쪽({@code applyDelta})에서 생기는데,
 * 응답을 만드는 곳은 그 바깥이다. 반환값에 얹어 올리려면 {@code applyDelta}를 부르는 모든
 * 경로의 시그니처를 바꿔야 한다 — 폐기·이고·조립·출고·정산·반품이 전부 걸린다.
 * 요청 범위 수집기를 하나 두면 안쪽은 담기만 하고 바깥은 {@link #drain()}으로 꺼내 쓴다.
 *
 * <p>‼️요청 범위다. 서비스에 주입해도 요청마다 새 인스턴스이므로 다른 사람의 경고가 섞이지 않는다.
 * {@code drain()}은 <b>꺼내면서 비운다</b> — 한 요청에서 응답을 두 번 만들 때 같은 경고가
 * 두 번 실리지 않게 한다.
 */
@Component
@RequestScope
public class StockWarningCollector {

    private final List<StockWarning> warnings = new ArrayList<>();

    public void add(StockWarning w) {
        warnings.add(w);
    }

    /** 쌓인 경고를 꺼내고 비운다. 없으면 빈 목록(응답에서 null 대신 []로 나가도록). */
    public List<StockWarning> drain() {
        List<StockWarning> copy = List.copyOf(warnings);
        warnings.clear();
        return copy;
    }
}
