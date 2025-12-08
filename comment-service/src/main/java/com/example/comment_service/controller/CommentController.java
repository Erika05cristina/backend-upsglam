package com.example.comment_service.controller;

import java.util.List;

import org.springframework.web.bind.annotation.*;
import com.example.comment_service.dto.CommentCreateRequest;
import com.example.comment_service.dto.CommentResponse;
import com.example.comment_service.service.CommentService;

import lombok.RequiredArgsConstructor;
import reactor.core.publisher.Mono;

@RestController
@RequestMapping("/comments")
@RequiredArgsConstructor
public class CommentController {

    private final CommentService service;

    @PostMapping
    public Mono<CommentResponse> addComment(@RequestBody CommentCreateRequest request) {
        return service.addComment(request);
    }

    @GetMapping("/{postId}")
    public Mono<List<CommentResponse>> getComments(@PathVariable String postId) {
        return service.getComments(postId);
    }
}
