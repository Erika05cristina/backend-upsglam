package com.example.auth_service.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import com.example.auth_service.auth.dto.AuthResponse;
import com.example.auth_service.auth.dto.RegisterRequest;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    public Mono<AuthResponse> registerUser(RegisterRequest request) {
        return Mono.fromCallable(() -> {
            UserRecord.CreateRequest createRequest = new UserRecord.CreateRequest()
                    .setEmail(request.email())
                    .setPassword(request.password())
                    .setDisplayName(request.displayName());

            UserRecord userRecord = FirebaseAuth.getInstance().createUser(createRequest);

            return new AuthResponse(
                    userRecord.getUid(),
                    userRecord.getEmail(),
                    userRecord.getDisplayName(),
                    null // el idToken normalmente lo genera el frontend con Firebase Client
            );
        }).subscribeOn(reactor.core.scheduler.Schedulers.boundedElastic());
    }

    // Más adelante podemos implementar login (via REST de Firebase) si tu profe lo exige sí o sí.
}
