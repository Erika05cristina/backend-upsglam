package com.example.user_service.service;

import com.example.user_service.dto.AddAvatarRequest;
import com.example.user_service.dto.CreateUserRequest;
import com.example.user_service.dto.UpdateUserRequest;
import com.example.user_service.model.User;
import com.example.user_service.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;

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
        List<String> avatarHistory = new ArrayList<>();
        if (StringUtils.hasText(req.getAvatarUrl())) {
            avatarHistory.add(req.getAvatarUrl());
            user.setAvatarUrl(req.getAvatarUrl());
        }
        user.setAvatarHistory(avatarHistory);
        user.setCreatedAt(System.currentTimeMillis());
        return repo.save(user);
    }

    public Mono<User> updateUser(String id, UpdateUserRequest req) {
        return repo.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")))
                .flatMap(u -> {
                    if (StringUtils.hasText(req.getName())) {
                        u.setName(req.getName());
                    }
                    if (StringUtils.hasText(req.getUsername())) {
                        u.setUsername(req.getUsername());
                    }
                    if (req.getBio() != null) {
                        u.setBio(req.getBio());
                    }

                    if (StringUtils.hasText(req.getAvatarUrl())) {
                        appendAvatar(u, req.getAvatarUrl());
                    }

                    return repo.save(u);
                });
    }

    public Mono<User> getUser(String id) {
        return repo.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
    }

    public Mono<User> addAvatar(String id, AddAvatarRequest request) {
        if (!StringUtils.hasText(request.getAvatarUrl())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "avatarUrl es requerido"));
        }

        return repo.findById(id)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")))
                .flatMap(user -> {
                    appendAvatar(user, request.getAvatarUrl());
                    return repo.save(user);
                });
    }

    private void appendAvatar(User user, String avatarUrl) {
        List<String> history = user.getAvatarHistory();
        if (history == null) {
            history = new ArrayList<>();
        } else if (!(history instanceof ArrayList<?>)) {
            history = new ArrayList<>(history);
        }
        if (history.isEmpty() || !avatarUrl.equals(history.get(history.size() - 1))) {
            history.add(avatarUrl);
        }
        user.setAvatarHistory(history);
        user.setAvatarUrl(avatarUrl);
    }
}
