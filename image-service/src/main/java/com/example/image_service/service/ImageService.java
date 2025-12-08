package com.example.image_service.service;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.image_service.dto.ImageUploadResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final WebClient supabaseClient;

    @Value("${supabase.anon-key}")
    private String supabaseKey;

    @Value("${supabase.bucket}")
    private String bucket;

    @Value("${supabase.url}")
    private String supabaseUrl;

    public Mono<ImageUploadResponse> uploadImage(FilePart filePart) {

        String fileName = UUID.randomUUID() + "-" + filePart.filename();

        return filePart.content()
                .reduce(new byte[0], (previous, dataBuffer) -> {
                    byte[] bytes = new byte[previous.length + dataBuffer.readableByteCount()];
                    System.arraycopy(previous, 0, bytes, 0, previous.length);
                    dataBuffer.read(bytes, previous.length, dataBuffer.readableByteCount());
                    return bytes;
                })
                .flatMap(bytes ->
                        supabaseClient.post()
                                .uri("/object/" + bucket + "/" + fileName)
                                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                                .header("Authorization", "Bearer " + supabaseKey)
                                .header("apikey", supabaseKey)
                                .header("x-upsert", "true")
                                .bodyValue(bytes)
                                .retrieve()
                                .onStatus(status -> status.isError(), response ->
                                        response.bodyToMono(String.class)
                                                .flatMap(error -> {
                                                    System.out.println("SUPABASE ERROR: " + error);
                                                    return Mono.error(new RuntimeException("Supabase error: " + error));
                                                })
                                )
                                                        .bodyToMono(String.class)
                                .map(r -> new ImageUploadResponse(
                                        supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName
                                ))
                );
    }
}
