package com.example.post_service.controller;

import com.example.post_service.dto.CreateCommentRequest;
import com.example.post_service.dto.CreatePostRequest;
import com.example.post_service.dto.PostResponse;
import com.example.post_service.service.PostService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping(value = "/posts", produces = MediaType.APPLICATION_JSON_VALUE)
@RequiredArgsConstructor             
public class PostController {

    private final PostService postService;   

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PostResponse> createPost(
            @RequestHeader("X-User-Uid") String userId,
            @Valid @RequestBody CreatePostRequest request
    ) {
        return postService.createPost(userId, request);
    }

    @GetMapping("/{id}")
    public Mono<PostResponse> getPost(@PathVariable String id) {
        return postService.getPost(id);
    }

    @GetMapping
    public Mono<List<PostResponse>> getAllPosts() {
        return postService.getAllPosts();
    }

    @GetMapping("/user/{userId}")
    public Mono<List<PostResponse>> getPostsByUser(@PathVariable String userId) {
        return postService.getPostsByUser(userId);
    }

    @PostMapping("/{id}/likes")
    public Mono<PostResponse> likePost(
            @PathVariable String id,
            @RequestHeader("X-User-Uid") String userId
    ) {
        return postService.likePost(id, userId);
    }

    @DeleteMapping("/{id}/likes")
    public Mono<PostResponse> unlikePost(
            @PathVariable String id,
            @RequestHeader("X-User-Uid") String userId
    ) {
        return postService.unlikePost(id, userId);
    }

    @PostMapping(value = "/{id}/comments", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PostResponse> addComment(
            @PathVariable String id,
            @RequestHeader("X-User-Uid") String userId,
            @Valid @RequestBody CreateCommentRequest request
    ) {
        return postService.addComment(id, userId, request);
    }
}
