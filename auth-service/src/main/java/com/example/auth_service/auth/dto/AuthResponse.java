package com.example.auth_service.auth.dto;

public record AuthResponse(
        String uid,
        String email,
        String displayName,
        String idToken   // luego lo llenamos o dejamos null
) {}
