package com.example.post_service.dto;

import lombok.Builder;
import lombok.Value;

@Value
@Builder
public class PostCommentResponse {
    String id;
    String userId;
    String text;
    long createdAt;
}
