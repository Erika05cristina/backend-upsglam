package com.example.auth_service.auth.controller;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;
import java.util.Map;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @PostMapping(value = "/register", produces = MediaType.APPLICATION_JSON_VALUE)
    public Mono<String> register(@RequestBody String request) {
        return Mono.just("Usuario registrado (demo): " + request);
    }

    @GetMapping("/test")
    public Mono<String> test() {
        return Mono.just("Auth service funcionando correctamente");
    }

    @GetMapping("/")
    public Map<String, String> home() {
        return Map.of("service", "Auth Service", "status", "running");
    }
}


