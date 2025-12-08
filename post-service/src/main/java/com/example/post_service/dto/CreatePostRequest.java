package com.example.post_service.dto;

import lombok.Data;

@Data
public class CreatePostRequest {
    private String content;
    private String imageUrl; // recibido desde media-service o flutter
}
