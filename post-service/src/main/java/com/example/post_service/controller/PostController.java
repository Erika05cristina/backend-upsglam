package com.example.post_service.controller;

import com.example.post_service.dto.CreatePostRequest;
import com.example.post_service.model.Post;
import com.example.post_service.service.PostService;
import lombok.RequiredArgsConstructor;     // ← IMPORTANTE
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Mono;

import java.util.List;

@RestController
@RequestMapping("/posts")
@RequiredArgsConstructor             
public class PostController {

    private final PostService postService;   

    @PostMapping
    public Mono<Post> createPost(
            @RequestHeader("X-User-Uid") String userId,
            @RequestBody CreatePostRequest request
    ) {
        return postService.createPost(userId, request);
    }

    @GetMapping("/{id}")
    public Mono<Post> getPost(@PathVariable String id) {
        return postService.getPost(id);
    }

    @GetMapping
    public Mono<List<Post>> getAllPosts() {
        return postService.getAllPosts();
    }
}
