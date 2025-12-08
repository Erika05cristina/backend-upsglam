package com.example.comment_service.dto;

import lombok.Data;

@Data
public class CommentCreateRequest {
    private String postId;
    private String userId;  
    private String text;
}
