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

    /**
     * 지원하지 않는 HTTP 메서드 → 405.
     *
     * <p>이게 없으면 경로는 맞는데 메서드만 틀린 호출이 500 "서버 오류"로 나간다.
     * 프론트가 원인을 찾을 수 없고, 서버가 진짜로 깨진 것처럼 보인다(실제로 겪었다).
     */
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMethodNotSupported(
            org.springframework.web.HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(ErrorCode.METHOD_NOT_ALLOWED.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.METHOD_NOT_ALLOWED,
                        "지원하지 않는 메서드입니다: " + e.getMethod())));
    }

    /**
     * 지원하지 않는 Content-Type → 415.
     *
     * <p>바로 위 405와 <b>같은 부류</b>인데 여기만 빠져 있었다. 파일 업로드
     * (매출 엑셀 업로드·송장 일괄 업로드)는 multipart만 받는데, JSON으로 호출하면
     * 마지막 Exception 핸들러까지 흘러 <b>500 "서버 오류"</b>가 나갔다.
     * 클라이언트 실수가 서버 장애로 보이고 ERROR 로그까지 남는다(전 API 점검에서 실제로 잡힘).
     *
     * <p>어떤 타입을 받는지 함께 알려준다 — 415만 던지면 프론트가 뭘 보내야 할지 모른다.
     */
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Void>> handleMediaTypeNotSupported(
            org.springframework.web.HttpMediaTypeNotSupportedException e) {
        String supported = e.getSupportedMediaTypes().stream()
                .map(Object::toString).collect(java.util.stream.Collectors.joining(", "));
        String detail = "지원하지 않는 Content-Type입니다: " + e.getContentType()
                + (supported.isBlank() ? "" : " (지원: " + supported + ")");
        return ResponseEntity.status(ErrorCode.UNSUPPORTED_MEDIA_TYPE.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.UNSUPPORTED_MEDIA_TYPE, detail)));
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
                        : bodyDetail(e);
        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(ApiResponse.fail(ErrorResponse.of(ErrorCode.INVALID_INPUT, detail)));
    }

    /**
     * 본문 해석 실패의 <b>진짜 이유</b>를 꺼낸다.
     *
     * <p>★예전엔 무조건 "요청 본문을 해석할 수 없습니다"였다. 그래서 enum 에 모르는 값을 보내도
     * <b>어느 필드가 무엇 때문인지</b> 알 수 없었다 — 우리 {@code @JsonCreator} 들이
     * "알 수 없는 대분류입니다: X (모의고사/교재/…)" 처럼 친절히 던지는데 그게 통째로 덮였다.
     * (2026-09-17 세부구분 추가 400·자재 등록 400 이 둘 다 이 문구만 보고 원인을 못 찾은 건이다.)
     *
     * <p>Jackson 은 우리 예외를 몇 겹 감싸므로 원인 사슬을 따라가 우리가 쓴 메시지를 찾는다.
     * 못 찾으면 <b>어느 필드</b>인지라도 알려 준다.
     */
    private static String bodyDetail(Exception e) {
        for (Throwable t = e.getCause(); t != null && t != t.getCause(); t = t.getCause()) {
            // 우리가 던진 메시지(한글 안내)를 만나면 그대로 쓴다.
            if (t instanceof IllegalArgumentException && t.getMessage() != null
                    && !t.getMessage().isBlank()) {
                return t.getMessage();
            }
            if (t instanceof com.fasterxml.jackson.databind.exc.InvalidFormatException ife) {
                String field = fieldOf(ife.getPath());
                return "값을 해석할 수 없습니다" + (field.isEmpty() ? "" : " — " + field)
                        + ": " + ife.getValue();
            }
        }
        if (e instanceof org.springframework.http.converter.HttpMessageNotReadableException
                && e.getCause() instanceof com.fasterxml.jackson.core.JsonProcessingException) {
            return "JSON 형식이 올바르지 않습니다.";
        }
        return "요청 본문을 해석할 수 없습니다.";
    }

    /** {@code items[0].shipmentType} 처럼 어느 필드에서 터졌는지. */
    private static String fieldOf(java.util.List<com.fasterxml.jackson.databind.JsonMappingException.Reference> path) {
        StringBuilder sb = new StringBuilder();
        for (var r : path) {
            if (r.getFieldName() != null) {
                if (sb.length() > 0) {
                    sb.append('.');
                }
                sb.append(r.getFieldName());
            } else if (r.getIndex() >= 0) {
                sb.append('[').append(r.getIndex()).append(']');
            }
        }
        return sb.toString();
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
