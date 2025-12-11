package com.example.post_service.model;

import java.util.ArrayList;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Post {
    private String id;
    private String userId;
    private String content;
    private String imageUrl;
    private long createdAt;
    private CudaMetadata cudaMetadata;

    @Builder.Default
    private List<String> likes = new ArrayList<>();

    @Builder.Default
    private List<PostComment> comments = new ArrayList<>();
}
