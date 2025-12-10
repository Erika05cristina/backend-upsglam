package com.example.post_service.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostComment {
    private String id;
    private String userId;
    private String text;
    private long createdAt;
}
