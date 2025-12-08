package com.example.auth_service.auth.service;

import com.example.auth_service.auth.dto.RegisterRequest;
import com.example.auth_service.auth.dto.LoginRequest;
import com.example.auth_service.auth.dto.LoginResponse;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    private final JwtService jwtService;

    public AuthService(JwtService jwtService) {
        this.jwtService = jwtService;
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
        return Mono.fromCallable(() -> {

            // 1. Validar idToken de Firebase
            FirebaseToken decoded = FirebaseAuth.getInstance().verifyIdToken(request.idToken());

            String uid = decoded.getUid();
            String email = decoded.getEmail();

            // 2. Generar JWT interno del backend
            String jwt = jwtService.generateToken(uid, email);

            return new LoginResponse(
                    uid,
                    email,
                    jwt,
                    "Login exitoso"
            );
        });
    }
}
