package com.firefly.ragdemo.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private Boolean success;
    private String message;
    private T data;
    private Integer code;
    private List<ValidationError> errors;

    @Data
    @Builder
    public static class ValidationError {
        private String field;
        private String message;
    }

    // 成功响应的静态方法
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message("操作成功")
                .data(data)
                .code(200)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .code(200)
                .build();
    }

    public static <T> ApiResponse<T> success(String message, T data, int code) {
        return ApiResponse.<T>builder()
                .success(true)
                .message(message)
                .data(data)
                .code(code)
                .build();
    }

    // 失败响应的静态方法
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .code(500)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int code) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .code(code)
                .build();
    }

    public static <T> ApiResponse<T> error(String message, int code, List<ValidationError> errors) {
        return ApiResponse.<T>builder()
                .success(false)
                .message(message)
                .code(code)
                .errors(errors)
                .build();
    }
}
