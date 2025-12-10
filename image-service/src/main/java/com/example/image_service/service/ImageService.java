package com.example.image_service.service;

import java.util.Base64;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.codec.multipart.FilePart;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.web.reactive.function.BodyInserters;

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

        // Normalizamos y validamos el filtro (solo estos 4)
        String filterType = filter.toLowerCase();
        if (!filterType.equals("mean") &&
            !filterType.equals("emboss") &&
            !filterType.equals("gaussian") &&
            !filterType.equals("sobel")) {

            return Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Filtro no permitido. Solo: mean, emboss, gaussian, sobel"
            ));
        }

        // Construimos el multipart/form-data que espera FastAPI:
        //   image: archivo
        //   filter_type: string
        //   kernel_size: int
        MultipartBodyBuilder builder = new MultipartBodyBuilder();

        builder
            .part("image", new ByteArrayResource(imageBytes) {
                @Override
                public String getFilename() {
                    return "upload.png"; // nombre ficticio
                }
            })
            .contentType(MediaType.APPLICATION_OCTET_STREAM);

        builder.part("filter_type", filterType);
        builder.part("kernel_size", String.valueOf(mask));

        return pythonClient.post()
                .uri("/api/convolucion")
                .contentType(MediaType.MULTIPART_FORM_DATA)
                .body(BodyInserters.fromMultipartData(builder.build()))
                .retrieve()
                .bodyToMono(byte[].class);  // 🔴 AQUÍ: esperamos bytes de imagen, no JSON
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
    public Mono<ImageUploadResponse> uploadAndProcess(FilePart file, int mask, String filter) {

        return filePartToBytes(file)
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
    public Mono<AvatarUploadResponse> uploadAvatar(FilePart file, String userId) {

        if (!StringUtils.hasText(userId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de usuario es obligatorio"));
        }

        HttpHeaders headers = file.headers();
        MediaType contentType = headers.getContentType();
        String originalFilename = file.filename();

        if (!isSupportedImage(contentType, originalFilename)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Solo se permiten PNG o JPG"));
        }

        return filePartToBytes(file)
                .flatMap(bytes -> {
                    // Validación tamaño máx 5 MB
                    if (bytes.length > 5 * 1024 * 1024) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El archivo excede el límite de 5MB"));
                    }

                    String ext = resolveExtension(contentType, originalFilename);
                    String fileName = "avatars/" + userId + "/" + UUID.randomUUID() + ext;

                    return uploadBytes(bytes, fileName)
                            .map(url -> new AvatarUploadResponse(url, System.currentTimeMillis()));
                });
    }

    private Mono<byte[]> filePartToBytes(FilePart file) {
        return DataBufferUtils.join(file.content())
                .map(this::toByteArray)
                .subscribeOn(Schedulers.boundedElastic());
    }

    private byte[] toByteArray(DataBuffer buffer) {
        try {
            byte[] bytes = new byte[buffer.readableByteCount()];
            buffer.read(bytes);
            return bytes;
        } finally {
            DataBufferUtils.release(buffer);
        }
    }

    private boolean isSupportedImage(MediaType contentType, String filename) {
        if (contentType != null) {
            if (MediaType.IMAGE_PNG.isCompatibleWith(contentType) || MediaType.IMAGE_JPEG.isCompatibleWith(contentType)) {
                return true;
            }
        }

        if (filename != null) {
            String lower = filename.toLowerCase();
            return lower.endsWith(".png") || lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        }

        return false;
    }

    private String resolveExtension(MediaType contentType, String filename) {
        if (contentType != null) {
            if (MediaType.IMAGE_PNG.isCompatibleWith(contentType)) {
                return ".png";
            }
            if (MediaType.IMAGE_JPEG.isCompatibleWith(contentType)) {
                return ".jpg";
            }
        }

        if (filename != null) {
            String lower = filename.toLowerCase();
            if (lower.endsWith(".png")) {
                return ".png";
            }
            if (lower.endsWith(".jpeg")) {
                return ".jpg";
            }
            if (lower.endsWith(".jpg")) {
                return ".jpg";
            }
        }

        // Fallback seguro
        return ".jpg";
    }
}
