package com.example.image_service.service;

import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;

import com.example.image_service.dto.ImageUploadResponse;
import com.example.image_service.dto.PythonRequest;
import com.example.image_service.dto.PythonResponse;
import com.example.image_service.dto.AvatarUploadResponse;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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


    // ===============================================================
    // 1) Procesamiento de imágenes via Python
    // ===============================================================
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


    // ===============================================================
    // 2) Subir bytes directamente a Supabase
    // ===============================================================
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


    // ===============================================================
    // 3) Upload + Process + Upload processed image
    // ===============================================================
    public Mono<ImageUploadResponse> uploadAndProcess(MultipartFile file, int mask, String filter) {

        return Mono.fromCallable(file::getBytes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(originalBytes -> {

                    String originalName = UUID.randomUUID() + "-original.png";
                    String processedName = UUID.randomUUID() + "-processed.png";

                    Mono<String> urlOriginal = uploadBytes(originalBytes, originalName);

                    Mono<String> urlProcessed =
                            processWithPython(originalBytes, mask, filter)
                                    .flatMap(processedBytes -> uploadBytes(processedBytes, processedName));

                    return Mono.zip(urlOriginal, urlProcessed)
                            .map(tuple -> new ImageUploadResponse(
                                    tuple.getT1(),  // original
                                    tuple.getT2()   // processed
                            ));
                });
    }


    // ===============================================================
    // 4) Upload Avatar (solo subir imagen, sin python)
    // ===============================================================
    public Mono<AvatarUploadResponse> uploadAvatar(MultipartFile file, String userId) {

        if (!StringUtils.hasText(userId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de usuario es obligatorio"));
        }

        // Validación tamaño máx 5 MB
        if (file.getSize() > 5 * 1024 * 1024) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo excede el límite de 5MB"));
        }

        // Validación tipo MIME
        String contentType = file.getContentType();
        if (!"image/png".equals(contentType) && !"image/jpeg".equals(contentType)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten PNG o JPG"));
        }

        return Mono.fromCallable(file::getBytes)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(bytes -> {

                    String ext = contentType.equals("image/png") ? ".png" : ".jpg";
                    String fileName = "avatars/" + userId + "/" + UUID.randomUUID() + ext;

                    return uploadBytes(bytes, fileName)
                            .map(url -> new AvatarUploadResponse(url, System.currentTimeMillis()));
                });
    }
}
