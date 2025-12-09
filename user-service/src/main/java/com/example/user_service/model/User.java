package com.example.user_service.model;

import lombok.Data;

@Data
public class User {
    private String id;
    private String name;
    private String username;
    private String bio;
    private String avatarUrl;
    private long createdAt;
}
