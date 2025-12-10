package com.example.post_service.model;

import lombok.Data;

@Data
public class Post {
    private String id;
    private String userId;
    private String content;
    private String imageUrl;
    private long createdAt;
}
