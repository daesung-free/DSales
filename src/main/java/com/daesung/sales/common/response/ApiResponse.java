package com.daesung.sales.common.response;

import lombok.Getter;

/**
 * 공통 API 응답 래퍼(envelope). 모든 컨트롤러 응답은 이 형태로 감싼다.
 * 성공: {success:true, data:..., error:null} / 실패: {success:false, data:null, error:{...}}
 */
@Getter
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorResponse error;

    private ApiResponse(boolean success, T data, ErrorResponse error) {
        this.success = success;
        this.data = data;
        this.error = error;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorResponse error) {
        return new ApiResponse<>(false, null, error);
    }
}
