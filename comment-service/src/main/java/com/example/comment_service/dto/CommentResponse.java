package com.example.comment_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class CommentResponse {
    private String id;
    private String postId;
    private String userId;
    private String text;
    private long timestamp;
}
