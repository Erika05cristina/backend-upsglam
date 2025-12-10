package com.example.auth_service.auth.service;

import com.example.auth_service.auth.dto.CreateUserProfileRequest;
import com.example.auth_service.auth.dto.RegisterRequest;
import com.example.auth_service.auth.dto.UserProfile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Component
public class UserServiceClient {

    private static final Logger log = LoggerFactory.getLogger(UserServiceClient.class);

    private final WebClient webClient;

    public UserServiceClient(@Value("${user-service.base-url}") String baseUrl, WebClient.Builder builder) {
        this.webClient = builder.baseUrl(baseUrl).build();
    }

    public Mono<UserProfile> fetchProfile(String userId) {
        return webClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserProfile.class)
                .onErrorResume(WebClientResponseException.NotFound.class, error -> Mono.empty())
                .onErrorResume(WebClientRequestException.class, error -> {
                    log.warn("No se pudo contactar al user-service para obtener el perfil del uid {}", userId, error);
                    return Mono.empty();
                })
                .onErrorResume(error -> {
                    log.warn("Error inesperado obteniendo el perfil del uid {}", userId, error);
                    return Mono.empty();
                });
    }

    public Mono<UserProfile> createOrUpdateProfile(String userId, RegisterRequest request) {
        return createProfile(
                userId,
                request.userName(),
                request.userName(),
                null,
                null
        );
    }

    public Mono<UserProfile> createProfile(String userId,
                                           String name,
                                           String username,
                                           String bio,
                                           String avatarUrl) {
        CreateUserProfileRequest body = new CreateUserProfileRequest(
                StringUtils.hasText(name) ? name : null,
                StringUtils.hasText(username) ? username : null,
                bio,
                StringUtils.hasText(avatarUrl) ? avatarUrl : null
        );

        return webClient.post()
                .uri("/users")
                .header("X-User-Uid", userId)
                .bodyValue(body)
                .retrieve()
                .bodyToMono(UserProfile.class)
                .onErrorResume(WebClientResponseException.Conflict.class, conflict -> {
                    log.warn("User-service devolvió 409 al crear perfil para uid {}. Intentando recuperar perfil existente", userId);
                    return fetchProfile(userId);
                })
                .onErrorResume(WebClientRequestException.class, error -> {
                    log.warn("No se pudo contactar al user-service para crear el perfil del uid {}", userId, error);
                    return Mono.empty();
                })
                .onErrorResume(error -> {
                    log.warn("Fallo al crear el perfil en user-service para uid {}", userId, error);
                    return Mono.empty();
                });
    }
}
