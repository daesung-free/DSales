package com.daesung.sales.receivable.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 수금 <b>묶음 등록</b>(현장 피드백 2026-10-14 B-5).
 *
 * <p>원문: "수금 여러 건 등록할 때 전표코드로 한 건 생성할 때 묶일 수 있도록".
 *
 * <p>★한 번 입금된 돈을 여러 줄로 쪼개 적는 일이 있다(거래처·항목별).
 * 그 줄들이 "같은 입금"이라는 사실이 지금은 어디에도 남지 않는다 —
 * 전표코드가 그걸 가리킨다.
 *
 * <p>★<b>줄마다 수금번호(C-)는 그대로 따로 생긴다.</b> 개별 수정·삭제가 그 번호로 되기 때문이다.
 * 전표코드는 그 위에 얹는 묶음일 뿐이다.
 */
@Schema(name = "CollectionBatchRequest", description = "수금 묶음 등록(한 전표코드로 여러 줄)")
public record CollectionBatchRequest(

        @Schema(description = """
                수금 줄 목록. 줄마다 거래처·유형·금액이 다를 수 있다.

                ‼️거래처를 섞어도 받는다 — 한 번 들어온 돈을 여러 거래처로 나눠 적는 경우가
                  실제로 있는지는 확인 중이라, 여기서 미리 막지 않는다.
                  막아야 한다면 그때 제약을 넣는 편이 낫다(먼저 막으면 쓸 수 있는 입력을 못 쓴다).""",
                requiredMode = Schema.RequiredMode.REQUIRED)
        @NotEmpty @Size(max = 200) @Valid List<CollectionRequest> lines
) {
}
