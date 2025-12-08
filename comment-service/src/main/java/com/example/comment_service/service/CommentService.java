package com.example.comment_service.service;

import com.example.comment_service.dto.CommentCreateRequest;
import com.example.comment_service.dto.CommentResponse;
import com.example.comment_service.model.Comment;
import com.example.comment_service.repository.CommentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class CommentService {

    private final CommentRepository repository;

    public Mono<CommentResponse> addComment(CommentCreateRequest req) {
        Comment comment = new Comment();
        comment.setPostId(req.getPostId());
        comment.setUserId(req.getUserId());
        comment.setText(req.getText());

        return repository.save(comment)
                .map(c -> new CommentResponse(
                        c.getId(),
                        c.getPostId(),
                        c.getUserId(),
                        c.getText(),
                        c.getTimestamp()
                ));
    }

    public Mono<java.util.List<CommentResponse>> getComments(String postId) {
        return repository.findByPostId(postId)
                .map(list -> list.stream()
                        .map(c -> new CommentResponse(
                                c.getId(),
                                c.getPostId(),
                                c.getUserId(),
                                c.getText(),
                                c.getTimestamp()
                        )).toList());
    }
}
