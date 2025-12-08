package com.example.auth_service.auth.controller;

import com.example.auth_service.auth.dto.RegisterRequest;
import com.example.auth_service.auth.service.AuthService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping(value = "/register", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<Map<String, Object>> register(@RequestBody RegisterRequest request) {

        return authService.register(request)
                .map(user -> Map.of(
                        "uid", user.getUid(),
                        "email", user.getEmail(),
                        "userName", user.getDisplayName(),
                        "message", "Usuario creado exitosamente en Firebase"
                ));
    }

    @GetMapping("/test")
    public Mono<String> test() {
        return Mono.just("Auth service funcionando correctamente");
    }
}
