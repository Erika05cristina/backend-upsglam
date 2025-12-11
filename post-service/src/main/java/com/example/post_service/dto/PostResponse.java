package com.example.post_service.dto;

import java.util.List;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PostResponse {
    private String id;
    private String userId;
    private String authorName;
    private String authorAvatarUrl;
    private String content;
    private String imageUrl;
    private long createdAt;
    private int likeCount;
    private List<String> likes;
    private List<PostCommentResponse> comments;
    private CudaMetadataDto cudaMetadata;
}
