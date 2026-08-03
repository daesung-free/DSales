package com.daesung.sales.batch.controller;

import com.daesung.sales.batch.dto.NotificationResponse;
import com.daesung.sales.batch.entity.Notification;
import com.daesung.sales.batch.repository.NotificationRepository;
import com.daesung.sales.common.dto.PageRequestDto;
import com.daesung.sales.common.exception.BusinessException;
import com.daesung.sales.common.exception.ErrorCode;
import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** 알림 조회·확인. 실제 경로: /api/v1/notifications. 담보만기 팝업(25p)의 데이터 원천. */
@Tag(name = "알림", description = "배치가 적재한 알림 조회·확인 처리")
@RestController
@RequiredArgsConstructor
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationRepository notificationRepository;

    @Operation(summary = "알림 목록", description = "최신순. unreadOnly=true면 미확인 건만(팝업용).")
    @GetMapping
    public ApiResponse<PageResponse<NotificationResponse>> list(
            @Parameter(description = "미확인만 조회(기본 false)")
            @RequestParam(required = false, defaultValue = "false") boolean unreadOnly,
            @ParameterObject PageRequestDto pageReq) {
        var page = unreadOnly
                ? notificationRepository.findByReadYnOrderByNotifyDateDescIdDesc(false, pageReq.toPageable())
                : notificationRepository.findAllByOrderByNotifyDateDescIdDesc(pageReq.toPageable());
        return ApiResponse.success(PageResponse.of(page.map(NotificationResponse::from)));
    }

    @Operation(summary = "알림 확인 처리", description = "팝업을 닫으면 다시 뜨지 않도록 읽음 표시.")
    @PutMapping("/{id}/read")
    @Transactional
    public ApiResponse<NotificationResponse> read(@PathVariable Long id) {
        Notification n = notificationRepository.findById(id)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND, "알림이 없습니다. id=" + id));
        n.markRead();
        return ApiResponse.success(NotificationResponse.from(n));
    }
}
