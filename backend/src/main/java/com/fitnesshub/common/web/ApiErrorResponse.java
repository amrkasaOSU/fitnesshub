package com.fitnesshub.common.web;

import java.util.List;

public record ApiErrorResponse(ApiError error) {

    public record ApiError(String code, String message, List<String> details) {
    }

    public static ApiErrorResponse of(String code, String message) {
        return new ApiErrorResponse(new ApiError(code, message, List.of()));
    }

    public static ApiErrorResponse of(String code, String message, List<String> details) {
        return new ApiErrorResponse(new ApiError(code, message, details));
    }
}
