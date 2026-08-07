package com.splitopt.backend.global.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import java.util.List;

/**
 * 모든 API 응답의 공통 포맷.
 * 성공: {@code ApiResponse.success(data)} / 실패: {@code ApiResponse.fail(message)}, {@code ApiResponse.fail(message, errors)}
 */
@Getter
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final String message;
    private final List<ErrorField> errors;

    private ApiResponse(boolean success, T data, String message, List<ErrorField> errors) {
        this.success = success;
        this.data = data;
        this.message = message;
        this.errors = errors;
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, data, null, null);
    }

    public static <T> ApiResponse<T> success(T data, String message) {
        return new ApiResponse<>(true, data, message, null);
    }

    public static ApiResponse<Void> success() {
        return new ApiResponse<>(true, null, null, null);
    }

    public static ApiResponse<Void> fail(String message) {
        return new ApiResponse<>(false, null, message, null);
    }

    public static ApiResponse<Void> fail(String message, List<ErrorField> errors) {
        return new ApiResponse<>(false, null, message, errors);
    }
    public static ApiResponse<Void> fail(String message, ErrorField error) {
        return fail(message, List.of(error));
    }
}
