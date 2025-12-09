package com.example.auth_service.auth.service;

import com.example.auth_service.auth.dto.FirebaseErrorResponse;
import com.example.auth_service.auth.dto.FirebaseSignInResponse;
import com.example.auth_service.auth.dto.RegisterRequest;
import com.example.auth_service.auth.dto.LoginRequest;
import com.example.auth_service.auth.dto.LoginResponse;
import com.example.auth_service.auth.exception.FirebaseAuthenticationException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

@Service
public class AuthService {

    private final JwtService jwtService;
    private final WebClient firebaseWebClient;
    private final String firebaseApiKey;

    private static final String FIREBASE_IDENTITY_BASE_URL = "https://identitytoolkit.googleapis.com/v1";

    public AuthService(JwtService jwtService,
                       WebClient.Builder webClientBuilder,
                       @Value("${firebase.api-key}") String firebaseApiKey) {
        this.jwtService = jwtService;
        this.firebaseApiKey = Objects.requireNonNull(firebaseApiKey, "firebase.api-key must be configured");
        if (!StringUtils.hasText(this.firebaseApiKey)) {
            throw new IllegalArgumentException("firebase.api-key must not be blank");
        }
        this.firebaseWebClient = webClientBuilder
                .baseUrl(FIREBASE_IDENTITY_BASE_URL)
                .build();
    }

    // ==========================
    //  REGISTRO
    // ==========================
    public Mono<UserRecord> register(RegisterRequest request) {
        return Mono.fromCallable(() -> {

            UserRecord.CreateRequest firebaseRequest = new UserRecord.CreateRequest()
                    .setEmail(request.email())
                    .setPassword(request.password())
                    .setDisplayName(request.userName());

            return FirebaseAuth.getInstance().createUser(firebaseRequest);
        });
    }

    // ==========================
    //  LOGIN
    // ==========================
    public Mono<LoginResponse> login(LoginRequest request) {
        if (!StringUtils.hasText(request.email()) || !StringUtils.hasText(request.password())) {
            return Mono.error(new FirebaseAuthenticationException("Email y contraseña son obligatorios"));
        }

        return firebaseWebClient.post()
                .uri(uriBuilder -> uriBuilder
                        .path("/accounts:signInWithPassword")
                        .queryParam("key", firebaseApiKey)
                        .build())
                .contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of(
                        "email", request.email(),
                        "password", request.password(),
                        "returnSecureToken", true
                ))
                .retrieve()
                .onStatus(HttpStatusCode::isError, response -> response
                        .bodyToMono(FirebaseErrorResponse.class)
                        .defaultIfEmpty(new FirebaseErrorResponse(null))
                        .flatMap(error -> {
                            String reason = "Firebase login failed";
                            if (error.error() != null) {
                                reason = String.format("Firebase login failed (%s): %s",
                                        error.error().code(),
                                        error.error().message());
                            }
                            return Mono.error(new FirebaseAuthenticationException(reason));
                        }))
                .bodyToMono(FirebaseSignInResponse.class)
                .map(firebaseResponse -> {
                    String uid = firebaseResponse.localId();
                    String email = firebaseResponse.email();

                    String jwt = jwtService.generateToken(uid, email);

                    return new LoginResponse(
                            uid,
                            email,
                            jwt,
                            firebaseResponse.idToken(),
                            firebaseResponse.refreshToken(),
                            "Login exitoso"
                    );
                });
    }
}
