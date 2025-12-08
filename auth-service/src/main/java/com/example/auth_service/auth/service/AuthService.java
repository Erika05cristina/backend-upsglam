package com.example.auth_service.auth.service;

import com.example.auth_service.auth.dto.RegisterRequest;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.UserRecord;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
public class AuthService {

    public Mono<UserRecord> register(RegisterRequest request) {

        return Mono.fromCallable(() -> {

            UserRecord.CreateRequest firebaseRequest = new UserRecord.CreateRequest()
                    .setEmail(request.email())
                    .setPassword(request.password())
                    .setDisplayName(request.userName());

            return FirebaseAuth.getInstance().createUser(firebaseRequest);
        });
    }
}
