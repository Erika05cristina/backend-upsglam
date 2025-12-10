package com.example.auth_service.auth.dto;

public record CreateUserProfileRequest(
        String name,
        String username,
        String bio,
        String avatarUrl
) {
}
