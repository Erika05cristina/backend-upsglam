package com.example.user_service.model;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class User {
    private String id;
    private String name;
    private String username;
    private String bio;
    private String avatarUrl;
    private List<String> avatarHistory = new ArrayList<>();
    private List<String> followers = new ArrayList<>();
    private List<String> following = new ArrayList<>();
    private long createdAt;
}
