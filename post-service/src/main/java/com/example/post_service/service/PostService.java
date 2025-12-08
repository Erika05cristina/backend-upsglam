package com.example.post_service.service;

import com.example.post_service.dto.CreatePostRequest;
import com.example.post_service.model.Post;
import com.example.post_service.repository.PostRepository;
import lombok.RequiredArgsConstructor;  // ← IMPORTANTE
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@RequiredArgsConstructor              // ← AGREGA ESTO
public class PostService {

    private final PostRepository postRepository;   // ← FINAL

    public Mono<Post> createPost(String userId, CreatePostRequest request) {
        Post post = new Post();
        post.setUserId(userId);
        post.setContent(request.getContent());
        post.setImageUrl(request.getImageUrl());
        post.setCreatedAt(System.currentTimeMillis());

        return postRepository.save(post);
    }

    public Mono<Post> getPost(String id) {
        return postRepository.findById(id);
    }

    public Mono<List<Post>> getAllPosts() {
        return postRepository.findAll();
    }
}
