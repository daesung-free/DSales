package com.daesung.sales.logistics.controller;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.logistics.dto.WorkTypeApplyResult;
import com.daesung.sales.logistics.dto.WorkTypeRequest;
import com.daesung.sales.logistics.dto.WorkTypeResponse;
import com.daesung.sales.logistics.service.WorkTypeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기초관리 - 물류비용등록 「작업구분 관리」 탭(36p). 실제 경로: /api/v1/masters/work-types.
 * 근거: 발주처 회신 2026-08-21 + 첨부 「물류비용등록_수정요청안_Page36_260821.pdf」.
 */
@Tag(name = "기초관리 · 작업구분",
        description = "작업구분별 기준단가 관리 + 매칭 상품 일괄 반영(36p)")
@RestController
@RequiredArgsConstructor
@RequestMapping("/masters/work-types")
public class WorkTypeController {

    private final WorkTypeService workTypeService;

    @Operation(summary = "작업구분 목록",
            description = """
                    작업구분과 기준단가 6종(시험지·OMR·단행본·라벨·기본작업비·출고비).

                    · 작업구분은 **고정값이 아니다** — 담당자가 추가·수정·삭제한다(발주처 확정).
                    · `packType`은 DSRE2 `tbl_logis_cost.PACKTYPE` 값이다.
                      DSRE2엔 그 숫자만 있고 **이름을 적을 자리가 없어** 우리가 갖는다.""")
    @GetMapping
    public ApiResponse<List<WorkTypeResponse>> list(
            @Parameter(description = "미사용 포함 여부(관리 화면에서만 true)")
            @RequestParam(required = false, defaultValue = "false") boolean includeUnused) {
        return ApiResponse.success(workTypeService.findAll(includeUnused));
    }

    @Operation(summary = "작업구분 등록",
            description = "PACKTYPE은 이후 바꿀 수 없다 — 단가 행과 잇는 유일한 키다.")
    @PostMapping
    public ApiResponse<WorkTypeResponse> create(@Valid @RequestBody WorkTypeRequest req) {
        return ApiResponse.success(workTypeService.create(req));
    }

    @Operation(summary = "작업구분 수정", description = "이름·기준단가·사용여부. **PACKTYPE은 무시된다**(불변).")
    @PutMapping("/{id}")
    public ApiResponse<WorkTypeResponse> update(@PathVariable Long id,
                                                @Valid @RequestBody WorkTypeRequest req) {
        return ApiResponse.success(workTypeService.update(id, req));
    }

    @Operation(summary = "작업구분 삭제",
            description = """
                    **그 작업구분을 쓰는 단가 행이 있으면 지우지 않고 사용여부만 끈다**(응답 `false`).
                    지워버리면 그 행들의 PACKTYPE 숫자가 무슨 뜻인지 알 수 없게 된다 —
                    DSRE2엔 이름이 없다.""")
    @DeleteMapping("/{id}")
    public ApiResponse<Boolean> delete(@PathVariable Long id) {
        return ApiResponse.success(workTypeService.delete(id));
    }

    @Operation(summary = "기준단가 일괄 반영",
            description = """
                    이 작업구분이 붙은 **단가 행 전체**에 기준단가를 밀어넣는다(36p ①).

                    · **예외로 등록된 행은 건너뛴다.** 담당자가 개별 수정한 행은 의도적으로 다른 값이라,
                      일괄적용에 조용히 덮이면 그 상품이 잘못된 단가로 청구된다.
                    · 건너뛴 행은 **시행코드까지** 돌려준다 — 건수만으론 예외가 지켜진 건지
                      누락인지 구분할 수 없다.
                    · 반영 후에도 **상품별 개별 수정은 그대로 가능**하다(그 순간 예외로 등록된다).

                    ⚠️마감 확정된 월의 물류작업비가 소급 변경되지 않게 하는 건 여기가 아니다 —
                    단가는 월별이 아니라 전역이라 이 단계에서 막을 수 없다.
                    마감 시점에 계산 결과를 굳혀 두는 것이 그 보장을 준다(별도 작업).""")
    @PostMapping("/{id}/apply")
    public ApiResponse<WorkTypeApplyResult> apply(@PathVariable Long id) {
        return ApiResponse.success(workTypeService.apply(id));
    }

    @Operation(summary = "기준단가 일괄 반영 — 미리보기",
            description = """
                    **아무것도 바꾸지 않고** "이대로 누르면 몇 건이 덮이는지"만 돌려준다(B-13).

                    ★이 동작은 **되돌릴 수 없다** — 덮이기 전 값이 어디에도 남지 않는다.
                    확인 없이 누르게 두면 작업구분을 잘못 고른 한 번으로 수백 행의 단가가 바뀌고
                    복구할 방법이 없다. 그래서 반영 전에 대상을 먼저 보여준다.

                    · 응답의 `preview=true`가 "안 바꿨다"는 표시다. **화면에서 반영 결과와 같은
                      문구로 보여주지 말 것** — 담당자가 이미 반영된 줄 안다.
                    · 반영과 **같은 코드로 센다**. 따로 세면 미리보기엔 12건인데 실제로는 15건이
                      바뀌는 상황이 생기고, 그러면 미리보기가 있으나 마나다.""")
    @GetMapping("/{id}/apply/preview")
    public ApiResponse<WorkTypeApplyResult> previewApply(@PathVariable Long id) {
        return ApiResponse.success(workTypeService.previewApply(id));
    }

    @Operation(summary = "예외 해제",
            description = "개별 수정 표시를 지운다. 이후 일괄 반영이 다시 이 행에도 적용된다.")
    @DeleteMapping("/overrides/{dtlCd}")
    public ApiResponse<Void> clearOverride(@PathVariable int dtlCd) {
        workTypeService.clearOverride(dtlCd);
        return ApiResponse.success(null);
    }
}
