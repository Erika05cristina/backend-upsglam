package com.example.comment_service.model;

import lombok.Data;

@Data
public class Comment {
    private String id;
    private String postId;
    private String userId;
    private String text;
    private long timestamp;
}
