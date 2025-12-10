package com.example.image_service.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.codec.multipart.FilePart;

import com.example.image_service.dto.ImageUploadResponse;
import com.example.image_service.dto.AvatarUploadResponse;
import com.example.image_service.service.ImageService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    // 1) Filtro + máscara (ya lo tenías)
    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ImageUploadResponse> uploadImage(
            @RequestPart("file") FilePart file,
            @RequestPart("mask") int mask,
            @RequestPart("filter") String filter
    ) {
        return imageService.uploadAndProcess(file, mask, filter);
    }

    // 2) NUEVO: subir avatar de usuario
    @PostMapping(
            value = "/avatar",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
        public Mono<AvatarUploadResponse> uploadAvatar(
                        @RequestHeader("X-User-Uid") String userId,
                        @RequestPart("file") FilePart file
        ) {
                return imageService.uploadAvatar(file, userId);
    }
}
