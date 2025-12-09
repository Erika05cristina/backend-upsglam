package com.example.user_service.controller;

import com.example.user_service.dto.CreateUserRequest;
import com.example.user_service.dto.UpdateUserRequest;
import com.example.user_service.model.User;
import com.example.user_service.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService service;

    @PostMapping
    public Mono<User> create(
            @RequestHeader("X-User-Uid") String userId,
            @RequestBody CreateUserRequest req
    ) {
        return service.createUser(userId, req);
    }

    @PutMapping("/{id}")
    public Mono<User> update(
            @PathVariable String id,
            @RequestBody UpdateUserRequest req
    ) {
        return service.updateUser(id, req);
    }

    @GetMapping("/{id}")
    public Mono<User> getUser(@PathVariable String id) {
        return service.getUser(id);
    }
}
