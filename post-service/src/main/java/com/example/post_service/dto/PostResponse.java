package com.example.post_service.dto;

import lombok.Data;

@Data
public class PostResponse {
    private String id;
    private String userId;
    private String content;
    private String imageUrl;
    private long createdAt;
}
