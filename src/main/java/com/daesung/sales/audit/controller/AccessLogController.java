package com.daesung.sales.audit.controller;

import com.daesung.sales.audit.entity.AccessAction;
import com.daesung.sales.audit.service.AccessLogService;
import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.query.MultiSelect;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 사용자 행위 기록 조회. 실제 경로: /api/v1/audit/access-log.
 *
 * <p>★<b>관리자만</b> 본다(SecurityConfig에서 막는다). 남의 다운로드 기록이 아무나 보이면
 * 그 자체가 감시로 읽힌다 — 발주처 선택(A).
 */
@Tag(name = "감사 · 사용자 행위기록",
        description = "누가 언제 무엇을 받아 갔는지. 관리자 전용.")
@RestController
@RequestMapping("/audit/access-log")
@RequiredArgsConstructor
public class AccessLogController {

    private final AccessLogService accessLogService;

    @Operation(summary = "사용자 행위기록 조회",
            description = """
                    아이디별 사용기록. 근거: 발주처 요청(2026-09-11) —
                    "직원의 **다운로드기록**같은 걸 볼 수 있게. 보안상 중요".

                    ### 무엇이 남는가
                    | 행위 | 남김 |
                    |---|---|
                    | **다운로드** | ✅ 파일명·바이트수까지. **이 표의 존재 이유다** |
                    | 로그인·로그아웃 | ✅ 실패한 로그인도 남는다 |
                    | 등록·수정·삭제 | ✅ |
                    | **조회(목록)** | ❌ 안 남긴다 |

                    ★조회를 남기지 않는 이유 — 목록 한 번 열 때마다 한 줄이 쌓이면 하루 수만 건이 되고,
                    **정작 봐야 할 다운로드 기록이 그 안에 묻힌다.**
                    단 다운로드는 GET이어도 남긴다(파일이 밖으로 나간 것이므로).

                    ### 읽는 법
                    · `success=false` 인 줄 — 막힌 시도다. 성공보다 중요할 때가 있다.
                    · `fileSize` — 얼마나 가져갔는지의 단서. 평소보다 큰 값이 눈에 띈다.
                    · `role` 은 **그때의 역할**이다. 나중에 권한이 바뀌어도 당시 상태를 알 수 있다.

                    ‼️요청 본문은 남기지 않는다(비밀번호·토큰이 들어 있다).
                    조회조건(`query`)은 남기되 token·password 류는 `***`로 지운다.

                    보존기간 1년. 경과분은 배치가 지운다.""")
    @GetMapping
    public ApiResponse<PageResponse<AccessLogService.AccessLogRow>> search(
            @Parameter(description = "시작일(yyyy-MM-dd). 미지정=제한 없음") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @Parameter(description = "종료일(yyyy-MM-dd). 그날 23:59:59까지 포함") @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @Parameter(description = "사용자 ID(부분일치)") @RequestParam(required = false) String username,
            @Parameter(description = "행위 — DOWNLOAD/LOGIN/LOGOUT/CREATE/UPDATE/DELETE (단건)")
            @RequestParam(required = false) AccessAction action,
            @Parameter(description = "행위 **다중선택** — 예: DOWNLOAD,DELETE")
            @RequestParam(required = false) List<AccessAction> actions,
            @Parameter(description = "성공만 보기(기본 false — 실패도 함께 본다)")
            @RequestParam(defaultValue = "false") boolean successOnly,
            @ParameterObject PageRequestDto pageReq) {
        // 종료일은 그날 끝까지 포함한다 — 날짜만 넣었는데 그날 오후 기록이 빠지면 못 찾는다.
        LocalDateTime to = (toDate == null) ? null : toDate.atTime(LocalTime.MAX);
        return ApiResponse.success(accessLogService.search(
                (fromDate == null) ? null : fromDate.atStartOfDay(), to,
                username, MultiSelect.merge(action, actions), successOnly, pageReq.toPageable()));
    }
}
