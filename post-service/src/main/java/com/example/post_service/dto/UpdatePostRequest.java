package com.example.post_service.dto;

import lombok.Data;

@Data
public class UpdatePostRequest {
    private String content;
    private String imageUrl;
}
