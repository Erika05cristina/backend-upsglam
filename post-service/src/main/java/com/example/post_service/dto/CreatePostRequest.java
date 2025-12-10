package com.example.post_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreatePostRequest {
    private String content;

    @NotBlank(message = "imageUrl es obligatorio")
    private String imageUrl; // recibido desde media-service o flutter
}
