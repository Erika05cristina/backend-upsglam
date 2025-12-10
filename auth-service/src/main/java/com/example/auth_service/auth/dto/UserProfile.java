package com.example.auth_service.auth.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserProfile(
        String id,
        String name,
        String username,
        String bio,
        String avatarUrl,
        List<String> avatarHistory,
        Long createdAt
) {
}
