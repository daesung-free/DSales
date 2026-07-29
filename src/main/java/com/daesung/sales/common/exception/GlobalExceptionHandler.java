package com.daesung.sales.common.exception;

import com.daesung.sales.common.response.ApiResponse;
import com.daesung.sales.common.response.ErrorResponse;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mapping.PropertyReferenceException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/** 전역 예외 처리 — 모든 예외를 공통 응답(ApiResponse.fail)으로 변환. */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    /** 도메인 비즈니스 예외. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusiness(BusinessException e) {
        ErrorCode code = e.getErrorCode();
        return ResponseEntity.status(code.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(code, e.getMessage())));
    }

    /** @Valid 검증 실패. */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException e) {
        String detail = e.getBindingResult().getFieldErrors().stream()
                .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                .collect(Collectors.joining(", "));
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.INVALID_INPUT, detail)));
    }

    /** 잘못된 정렬(sort) 컬럼 등 → 400. */
    @ExceptionHandler(PropertyReferenceException.class)
    public ResponseEntity<ApiResponse<Void>> handlePropertyReference(PropertyReferenceException e) {
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.INVALID_INPUT,
                        "정렬/조회 기준이 올바르지 않습니다: " + e.getPropertyName())));
    }

    /** 존재하지 않는 경로/리소스 → 404. */
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(NoResourceFoundException e) {
        return ResponseEntity.status(ErrorCode.NOT_FOUND.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.NOT_FOUND, e.getMessage())));
    }

    /**
     * 잘못된 파라미터 타입(enum·날짜 등)·읽을 수 없는 본문(JSON 오류)·필수 파라미터 누락 → 400.
     * (없으면 마지막 Exception 핸들러로 흘러 500 + ERROR 로그 오염 → 정상 클라이언트 실수가 장애로 오탐)
     */
    @ExceptionHandler({
            org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class
    })
    public ResponseEntity<ApiResponse<Void>> handleBadRequest(Exception e) {
        String detail = (e instanceof org.springframework.web.method.annotation.MethodArgumentTypeMismatchException me)
                ? "파라미터 '" + me.getName() + "' 값이 올바르지 않습니다."
                : (e instanceof org.springframework.web.bind.MissingServletRequestParameterException mp)
                        ? "필수 파라미터 누락: " + mp.getParameterName()
                        : "요청 본문을 해석할 수 없습니다.";
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.INVALID_INPUT, detail)));
    }

    /** 메서드 보안(@PreAuthorize) 권한 부족 → 403. (경로 규칙 거부는 SecurityConfig에서 처리) */
    @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Void>> handleAccessDenied(
            org.springframework.security.access.AccessDeniedException e) {
        return ResponseEntity.status(ErrorCode.FORBIDDEN.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.FORBIDDEN)));
    }

    /** 처리되지 않은 그 외 예외. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleEtc(Exception e) {
        log.error("Unhandled exception", e);
        return ResponseEntity.status(ErrorCode.INTERNAL_ERROR.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.INTERNAL_ERROR)));
    }
}
