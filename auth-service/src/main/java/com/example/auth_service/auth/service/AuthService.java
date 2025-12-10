package com.example.auth_service.auth.service;

import com.example.auth_service.auth.dto.FirebaseErrorResponse;
import com.example.auth_service.auth.dto.FirebaseSignInResponse;
import com.example.auth_service.auth.dto.LoginRequest;
import com.example.auth_service.auth.dto.LoginResponse;
import com.example.auth_service.auth.dto.RegisterRequest;
import com.example.auth_service.auth.dto.UserProfile;
import com.example.auth_service.auth.exception.FirebaseAuthenticationException;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final String FIREBASE_IDENTITY_BASE_URL = "https://identitytoolkit.googleapis.com/v1";
    private static final String SIGN_IN_PATH = "/accounts:signInWithPassword";

    private final JwtService jwtService;
    private final UserServiceClient userServiceClient;
    private final WebClient firebaseWebClient;
    private final String firebaseApiKey;

    public AuthService(JwtService jwtService,
                       UserServiceClient userServiceClient,
                       WebClient.Builder webClientBuilder,
                       @Value("${firebase.api-key}") String firebaseApiKey) {
        this.jwtService = jwtService;
        this.userServiceClient = userServiceClient;
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
        }).flatMap(userRecord -> userServiceClient
                .createOrUpdateProfile(userRecord.getUid(), request)
                .doOnError(error -> log.warn("No se pudo crear el perfil en user-service para el uid {}", userRecord.getUid(), error))
                .onErrorResume(error -> Mono.empty())
                .thenReturn(userRecord));
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
                        .path(SIGN_IN_PATH)
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
                .flatMap(firebaseResponse -> userServiceClient
                        .fetchProfile(firebaseResponse.localId())
                        .map(profile -> buildLoginResponse(firebaseResponse, profile))
                        .switchIfEmpty(Mono.fromSupplier(() -> buildLoginResponse(firebaseResponse, null))));
    }

    private LoginResponse buildLoginResponse(FirebaseSignInResponse firebaseResponse, UserProfile profile) {
        String uid = firebaseResponse.localId();
        String email = firebaseResponse.email();

        String jwt = jwtService.generateToken(uid, email);

        return new LoginResponse(
                uid,
                email,
                jwt,
                firebaseResponse.idToken(),
                firebaseResponse.refreshToken(),
                "Login exitoso",
                profile
        );
    }
}
