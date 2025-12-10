package com.example.auth_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FirebaseSignInResponse(
        @JsonProperty("localId") String localId,
        String email,
        @JsonProperty("displayName") String displayName,
        @JsonProperty("idToken") String idToken,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("expiresIn") String expiresIn
) {}
