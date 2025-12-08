package com.example.image_service.service;

import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;

import com.example.image_service.dto.ImageUploadResponse;
import com.example.image_service.dto.PythonRequest;
import com.example.image_service.dto.PythonResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class ImageService {

    private final WebClient supabaseClient;
    private final WebClient pythonClient;

    @Value("${supabase.anon-key}")
    private String supabaseKey;

    @Value("${supabase.bucket}")
    private String bucket;

    @Value("${supabase.url}")
    private String supabaseUrl;

    // -------------------------------------------
    // 1) Process image with Python
    // -------------------------------------------
    private Mono<byte[]> processWithPython(byte[] imageBytes, int mask, String filter) {

        String base64 = Base64.getEncoder().encodeToString(imageBytes);

        PythonRequest req = new PythonRequest(base64, mask, filter);

        return pythonClient.post()
                .uri("/process")
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(req)
                .retrieve()
                .bodyToMono(PythonResponse.class)
                .map(resp -> Base64.getDecoder().decode(resp.getProcessedImageBase64()));
    }

    // -------------------------------------------
    // 2) Upload raw bytes to Supabase
    // -------------------------------------------
    private Mono<String> uploadBytes(byte[] bytes, String fileName) {

        return supabaseClient.post()
                .uri("/object/" + bucket + "/" + fileName)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Authorization", "Bearer " + supabaseKey)
                .header("apikey", supabaseKey)
                .header("x-upsert", "true")
                .bodyValue(bytes)
                .retrieve()
                .bodyToMono(String.class)
                .map(r -> supabaseUrl + "/storage/v1/object/public/" + bucket + "/" + fileName);
    }

    // -------------------------------------------
    // 3) MAIN: Upload image + process + upload processed image
    // -------------------------------------------
    public Mono<ImageUploadResponse> uploadAndProcess(MultipartFile file, int mask, String filter) {

        return Mono.fromCallable(file::getBytes)
                .flatMap(originalBytes -> {

                    String originalName = UUID.randomUUID() + "-original.png";
                    String processedName = UUID.randomUUID() + "-processed.png";

                    Mono<String> originalUrlMono = uploadBytes(originalBytes, originalName);

                    Mono<String> processedUrlMono =
                            processWithPython(originalBytes, mask, filter)
                                    .flatMap(processedBytes -> uploadBytes(processedBytes, processedName));

                    return Mono.zip(originalUrlMono, processedUrlMono)
                            .map(tuple -> new ImageUploadResponse(
                                    tuple.getT1(),
                                    tuple.getT2()
                            ));
                });
    }
}
