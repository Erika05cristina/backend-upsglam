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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.text.Normalizer;
import java.util.Locale;
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
    private final String defaultAvatarUrl;

    public AuthService(JwtService jwtService,
                       UserServiceClient userServiceClient,
                       WebClient.Builder webClientBuilder,
                       @Value("${firebase.api-key}") String firebaseApiKey,
                       @Value("${user-service.default-avatar-url:}") String defaultAvatarUrl) {
        this.jwtService = jwtService;
        this.userServiceClient = userServiceClient;
        this.firebaseApiKey = Objects.requireNonNull(firebaseApiKey, "firebase.api-key must be configured");
        if (!StringUtils.hasText(this.firebaseApiKey)) {
            throw new IllegalArgumentException("firebase.api-key must not be blank");
        }
        this.firebaseWebClient = webClientBuilder
                .baseUrl(FIREBASE_IDENTITY_BASE_URL)
                .build();
        this.defaultAvatarUrl = defaultAvatarUrl;
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
        }).flatMap(userRecord -> {
            String uid = userRecord.getUid();
            String displayName = request.userName();
            String username = sanitizeUsername(displayName, uid);
            String avatarUrl = resolveAvatarUrl(displayName);

            return userServiceClient
                .createProfile(uid, displayName, username, null, avatarUrl)
                .doOnError(error -> log.warn("No se pudo crear el perfil en user-service para el uid {}", uid, error))
                .onErrorResume(error -> Mono.empty())
                .thenReturn(userRecord);
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
                .flatMap(firebaseResponse -> {
                    String uid = firebaseResponse.localId();
                    String name = determineDisplayName(firebaseResponse);
                    String username = determineUsername(firebaseResponse);
                    String avatarUrl = resolveAvatarUrl(name);

                    Mono<UserProfile> profileMono = userServiceClient
                            .fetchProfile(uid)
                            .switchIfEmpty(userServiceClient
                                    .createProfile(uid, name, username, null, avatarUrl)
                                    .switchIfEmpty(Mono.empty()));

                    return mapToLoginResponse(firebaseResponse, profileMono);
                });
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

    private Mono<LoginResponse> mapToLoginResponse(FirebaseSignInResponse firebaseResponse, Mono<UserProfile> profileMono) {
        return profileMono
                .map(profile -> buildLoginResponse(firebaseResponse, profile))
                .switchIfEmpty(Mono.fromSupplier(() -> buildLoginResponse(firebaseResponse, null)));
    }

    private String determineDisplayName(FirebaseSignInResponse response) {
        if (StringUtils.hasText(response.displayName())) {
            return response.displayName();
        }
        String email = response.email();
        if (StringUtils.hasText(email)) {
            return extractLocalPart(email);
        }
        return "Usuario";
    }

    private String determineUsername(FirebaseSignInResponse response) {
        String base = null;
        if (StringUtils.hasText(response.email())) {
            base = extractLocalPart(response.email());
        }
        if (!StringUtils.hasText(base) && StringUtils.hasText(response.displayName())) {
            base = response.displayName();
        }
        return sanitizeUsername(base, response.localId());
    }

    private String sanitizeUsername(String candidate, String fallbackSeed) {
        String base = StringUtils.hasText(candidate) ? candidate : fallbackSeed;
        if (!StringUtils.hasText(base)) {
            base = "user";
        }

        String normalized = Normalizer.normalize(base, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .replaceAll("[^A-Za-z0-9]", "")
                .toLowerCase(Locale.ROOT);

        if (StringUtils.hasText(normalized)) {
            return normalized;
        }

        String fallback = StringUtils.hasText(fallbackSeed) ? fallbackSeed : "user";
        fallback = fallback.replaceAll("[^A-Za-z0-9]", "");
        if (fallback.length() > 8) {
            fallback = fallback.substring(0, 8);
        }
        return StringUtils.hasText(fallback) ? "user" + fallback : "user";
    }

    private String resolveAvatarUrl(String seed) {
        if (!StringUtils.hasText(defaultAvatarUrl)) {
            return null;
        }
        String value = StringUtils.hasText(seed) ? seed : "User";
        if (defaultAvatarUrl.contains("%s")) {
            String encoded = urlEncode(value);
            return String.format(Locale.ROOT, defaultAvatarUrl, encoded);
        }
        return defaultAvatarUrl;
    }

    private String extractLocalPart(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex > 0) {
            return email.substring(0, atIndex);
        }
        return email;
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
