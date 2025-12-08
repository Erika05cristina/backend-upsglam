package com.example.image_service.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.example.image_service.dto.ImageUploadResponse;
import com.example.image_service.service.ImageService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/images")
@RequiredArgsConstructor
public class ImageController {

    private final ImageService imageService;

    @PostMapping(
            value = "/upload",
            consumes = MediaType.MULTIPART_FORM_DATA_VALUE
    )
    public Mono<ImageUploadResponse> uploadImage(
            @RequestPart("file") MultipartFile file,
            @RequestPart("mask") int mask,
            @RequestPart("filter") String filter
    ) {
        return imageService.uploadAndProcess(file, mask, filter);
    }
}
