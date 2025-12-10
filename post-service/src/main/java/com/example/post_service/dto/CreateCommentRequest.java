package com.example.post_service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCommentRequest {
    @NotBlank(message = "El comentario no puede estar vacío")
    private String text;
}
