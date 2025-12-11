package com.example.post_service.client;

import java.time.Duration;

import com.example.post_service.dto.UserProfile;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

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
}
