package com.example.auth_service.auth.dto;

public record LoginResponse(
        String uid,
        String email,
        String jwt,
        String message
) {}
