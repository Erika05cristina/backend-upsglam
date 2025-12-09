package com.example.user_service.dto;

import lombok.Data;

@Data
public class CreateUserRequest {
    private String name;
    private String username;
    private String bio;
    private String avatarUrl;
}
