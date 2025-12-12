package com.example.user_service.service;

import com.example.user_service.dto.AddAvatarRequest;
import com.example.user_service.dto.CreateUserRequest;
import com.example.user_service.dto.FollowActionResponse;
import com.example.user_service.dto.FollowersResponse;
import com.example.user_service.dto.FollowingResponse;
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
        user.setFollowers(new ArrayList<>());
        user.setFollowing(new ArrayList<>());
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

    public Mono<List<User>> listUsers() {
        return repo.findAll()
                .map(list -> list.isEmpty() ? List.of() : List.copyOf(list));
    }

    public Mono<User> getUserByUsername(String username) {
        if (!StringUtils.hasText(username)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "username es requerido"));
        }

        return repo.findByUsername(username.trim())
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")));
    }

    public Mono<FollowActionResponse> followUser(String currentUserId, String targetUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Uid es requerido"));
        }
        if (currentUserId.equals(targetUserId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes seguirte a ti mismo"));
        }

        Mono<User> currentMono = repo.findById(currentUserId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario actual no encontrado")));

        Mono<User> targetMono = repo.findById(targetUserId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario objetivo no encontrado")));

        return Mono.zip(currentMono, targetMono)
                .flatMap(tuple -> {
                    User current = tuple.getT1();
                    User target = tuple.getT2();

                    List<String> currentFollowing = ensureList(current.getFollowing());
                    if (currentFollowing.contains(targetUserId)) {
                        return Mono.just(buildFollowActionResponse(target, current));
                    }

                    List<String> targetFollowers = ensureList(target.getFollowers());

                    if (targetFollowers.size() >= 10) {
                        return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El usuario alcanzó el límite de seguidores"));
                    }

                    currentFollowing.add(targetUserId);
                    targetFollowers.add(currentUserId);

                    current.setFollowing(currentFollowing);
                    target.setFollowers(targetFollowers);

                    return Mono.zip(repo.save(current), repo.save(target))
                            .thenReturn(buildFollowActionResponse(target, current));
                });
    }

    public Mono<FollowActionResponse> unfollowUser(String currentUserId, String targetUserId) {
        if (!StringUtils.hasText(currentUserId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "X-User-Uid es requerido"));
        }
        if (currentUserId.equals(targetUserId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "No puedes dejar de seguirte a ti mismo"));
        }

        Mono<User> currentMono = repo.findById(currentUserId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario actual no encontrado")));

        Mono<User> targetMono = repo.findById(targetUserId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario objetivo no encontrado")));

        return Mono.zip(currentMono, targetMono)
                .flatMap(tuple -> {
                    User current = tuple.getT1();
                    User target = tuple.getT2();

                    List<String> currentFollowing = ensureList(current.getFollowing());
                    List<String> targetFollowers = ensureList(target.getFollowers());

                    boolean removed = currentFollowing.remove(targetUserId);
                    if (removed) {
                        targetFollowers.remove(currentUserId);
                        current.setFollowing(currentFollowing);
                        target.setFollowers(targetFollowers);

                        return Mono.zip(repo.save(current), repo.save(target))
                                .thenReturn(buildFollowActionResponse(target, current));
                    }

                    return Mono.just(buildFollowActionResponse(target, current));
                });
    }

    public Mono<FollowersResponse> listFollowers(String userId) {
        return repo.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")))
            .map(user -> {
                List<String> followers = ensureList(user.getFollowers());
                return FollowersResponse.builder()
                    .userId(user.getId())
                    .count(followers.size())
                    .followers(List.copyOf(followers))
                    .build();
            });
    }

    public Mono<FollowingResponse> listFollowing(String userId) {
        return repo.findById(userId)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado")))
            .map(user -> {
                List<String> following = ensureList(user.getFollowing());
                return FollowingResponse.builder()
                    .userId(user.getId())
                    .count(following.size())
                    .following(List.copyOf(following))
                    .build();
            });
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
        List<String> history = ensureList(user.getAvatarHistory());
        if (history.isEmpty() || !avatarUrl.equals(history.get(history.size() - 1))) {
            history.add(avatarUrl);
        }
        user.setAvatarHistory(history);
        user.setAvatarUrl(avatarUrl);
    }

    private List<String> ensureList(List<String> list) {
        if (list == null) {
            return new ArrayList<>();
        }
        if (list instanceof ArrayList<?>) {
            return list;
        }
        return new ArrayList<>(list);
    }

    private FollowActionResponse buildFollowActionResponse(User target, User current) {
        List<String> targetFollowers = ensureList(target.getFollowers());
        List<String> currentFollowing = ensureList(current.getFollowing());

        return FollowActionResponse.builder()
                .targetUserId(target.getId())
                .followersCount(targetFollowers.size())
                .followingCount(currentFollowing.size())
                .following(currentFollowing.contains(target.getId()))
                .build();
    }
}
