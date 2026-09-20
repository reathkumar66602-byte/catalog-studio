package com.catalogstudio.common.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<FieldError> errors,
        String traceId
) {

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, null, null);
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ok("OK", data);
    }

    public static ApiResponse<Void> okMessage(String message) {
        return new ApiResponse<>(true, message, null, null, null);
    }

    public static ApiResponse<Void> validation(String message, List<FieldError> errors) {
        return new ApiResponse<>(false, message, null, errors, null);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>(false, message, null, null, null);
    }

    public static ApiResponse<Void> error(String message, String traceId) {
        return new ApiResponse<>(false, message, null, null, traceId);
    }

    public record FieldError(String field, String message) {}
}
