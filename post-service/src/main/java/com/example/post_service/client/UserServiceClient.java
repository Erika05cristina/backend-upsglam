package com.example.post_service.client;

import java.time.Duration;

import com.example.post_service.dto.FollowingListResponse;
import com.example.post_service.dto.UserProfile;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;
import org.springframework.web.server.ResponseStatusException;

@Component
public class UserServiceClient {

    private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(3);

    private final WebClient userServiceWebClient;

    public UserServiceClient(WebClient userServiceWebClient) {
        this.userServiceWebClient = userServiceWebClient;
    }

    public Mono<UserProfile> getUserProfile(String userId) {
        return userServiceWebClient.get()
                .uri("/users/{id}", userId)
                .retrieve()
                .bodyToMono(UserProfile.class)
                .timeout(DEFAULT_TIMEOUT)
                .onErrorResume(WebClientResponseException.NotFound.class, ex -> Mono.empty())
                .onErrorResume(WebClientResponseException.class, ex -> Mono.empty())
                .onErrorResume(Exception.class, ex -> Mono.empty());
    }

    public Mono<List<String>> getFollowingIds(String userId) {
        return userServiceWebClient.get()
                .uri("/users/{id}/following", userId)
                .retrieve()
                .bodyToMono(FollowingListResponse.class)
                .timeout(DEFAULT_TIMEOUT)
                .map(response -> {
                    List<String> following = response.getFollowing();
                    if (following == null || following.isEmpty()) {
                        return List.<String>of();
                    }
                    return List.copyOf(following);
                })
                .onErrorResume(throwable -> {
                    if (throwable instanceof WebClientResponseException.NotFound) {
                        return Mono.<List<String>>error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado"));
                    }
                    if (throwable instanceof WebClientResponseException webClientException) {
                        return Mono.<List<String>>error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error consultando user-service", webClientException));
                    }
                    return Mono.<List<String>>error(new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Error consultando user-service", throwable));
                });
    }
}
