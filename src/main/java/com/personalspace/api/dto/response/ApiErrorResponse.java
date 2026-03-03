package com.personalspace.api.dto.response;

import java.time.Instant;
import java.util.Map;

public record ApiErrorResponse(
        int status,
        String message,
        Map<String, String> errors,
        Instant timestamp
) {
    public ApiErrorResponse(int status, String message) {
        this(status, message, null, Instant.now());
    }

    public ApiErrorResponse(int status, String message, Map<String, String> errors) {
        this(status, message, errors, Instant.now());
    }
}
