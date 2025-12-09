package com.example.auth_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FirebaseErrorResponse(
        FirebaseError error
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record FirebaseError(
            int code,
            String message,
            List<ErrorDetail> errors
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ErrorDetail(
            String message,
            String domain,
            String reason
    ) {}
}
