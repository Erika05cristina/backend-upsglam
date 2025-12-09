package com.example.user_service.service;

import com.example.user_service.dto.CreateUserRequest;
import com.example.user_service.dto.UpdateUserRequest;
import com.example.user_service.model.User;
import com.example.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository repo;

    public Mono<User> createUser(String userId, CreateUserRequest req) {
        User user = new User();
        user.setId(userId);
        user.setName(req.getName());
        user.setUsername(req.getUsername());
        user.setBio(req.getBio());
        user.setAvatarUrl(req.getAvatarUrl());
        user.setCreatedAt(System.currentTimeMillis());
        return repo.save(user);
    }

    public Mono<User> updateUser(String id, UpdateUserRequest req) {
        return repo.findById(id)
                .flatMap(u -> {
                    u.setName(req.getName());
                    u.setUsername(req.getUsername());
                    u.setBio(req.getBio());
                    u.setAvatarUrl(req.getAvatarUrl());
                    return repo.save(u);
                });
    }

    public Mono<User> getUser(String id) {
        return repo.findById(id);
    }
}
