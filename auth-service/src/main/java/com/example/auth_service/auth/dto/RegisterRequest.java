package com.example.auth_service.auth.dto;

public record RegisterRequest(
        String email,
        String password,
        String displayName
) {}
