package com.daesung.sales.partner.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

/** 거래처 DSRE 코드 동기화 결과. 학교 동기화({@code SchoolSyncResult})와 같은 모양으로 낸다. */
@Schema(description = "거래처 DSRE 코드 동기화 결과")
public record DsreCodeSyncResult(

        @Schema(description = "DSRE2 tbl_cust_info 에서 읽은 행 수", example = "214")
        int fetched,

        @Schema(description = "새로 채운 수(비어 있던 칸)", example = "198")
        int filled,

        @Schema(description = "값이 바뀌어 갱신한 수", example = "2")
        int changed,

        @Schema(description = "이미 같은 값이라 건드리지 않은 수", example = "12")
        int unchanged,

        @Schema(description = """
                DSRE 에는 있는데 **우리 거래처 마스터에 없는** 매출코드 수.
                0이 아니면 거래처 마스터가 덜 들어왔다는 뜻이라 확인 대상이다.""", example = "4")
        int unmatched,

        @Schema(description = """
                ‼️**같은 DSRE 코드를 여럿이 쓰는 경우**(코드 목록).
                인덱스를 UNIQUE 로 걸지 않은 이유가 이것이다 — 막으면 동기화가 통째로 실패한다.
                비어 있어야 정상이고, 값이 있으면 그 코드로 조인할 때 학교가 엉뚱한 거래처에 붙는다.""")
        List<String> duplicatedDsreCodes
) {
}
