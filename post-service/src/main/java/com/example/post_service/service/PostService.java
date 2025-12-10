package com.example.post_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import com.example.post_service.dto.CreatePostRequest;
import com.example.post_service.dto.CreateCommentRequest;
import com.example.post_service.dto.PostCommentResponse;
import com.example.post_service.dto.PostResponse;
import com.example.post_service.model.Post;
import com.example.post_service.model.PostComment;
import com.example.post_service.repository.PostRepository;

import com.google.api.gax.rpc.ApiException;
import com.google.api.gax.rpc.StatusCode;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;

        public Mono<PostResponse> createPost(String userId, CreatePostRequest request) {
        if (request == null || !StringUtils.hasText(request.getImageUrl())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageUrl es obligatorio"));
        }

        return requireUser(userId)
            .map(validUserId -> Post.builder()
                .userId(validUserId)
                .content(StringUtils.hasText(request.getContent()) ? request.getContent().trim() : null)
                .imageUrl(request.getImageUrl().trim())
                .createdAt(System.currentTimeMillis())
                .build())
            .flatMap(postRepository::save)
            .map(this::ensureCollections)
            .map(this::toResponse);
    }

    public Mono<PostResponse> getPost(String id) {
        return postRepository.findById(id)
                .map(this::ensureCollections)
                .map(this::toResponse)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post no encontrado")));
    }

        public Mono<List<PostResponse>> getAllPosts() {
        return postRepository.findAll()
            .defaultIfEmpty(List.of())
            .map(posts -> posts.stream()
                .map(this::ensureCollections)
                .map(this::toResponse)
                .collect(Collectors.toList()));
        }

            public Mono<List<PostResponse>> getPostsByUser(String userId) {
            return requireUser(userId)
                .flatMap(postRepository::findByUserId)
                .defaultIfEmpty(List.of())
                .map(posts -> posts.stream()
                    .map(this::ensureCollections)
                    .map(this::toResponse)
                    .collect(Collectors.toList()));
    }

        public Mono<PostResponse> likePost(String postId, String userId) {
        return requireUser(userId)
            .flatMap(validUserId -> postRepository.addLike(postId, validUserId)
                .onErrorMap(throwable -> translateFirestoreException(throwable, postId)))
            .then(getPost(postId));
        }

        public Mono<PostResponse> unlikePost(String postId, String userId) {
        return requireUser(userId)
            .flatMap(validUserId -> postRepository.removeLike(postId, validUserId)
                .onErrorMap(throwable -> translateFirestoreException(throwable, postId)))
            .then(getPost(postId));
        }

        public Mono<PostResponse> addComment(String postId, String userId, CreateCommentRequest request) {
        if (request == null || !StringUtils.hasText(request.getText())) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comentario no puede estar vacío"));
        }

        String trimmedText = request.getText().trim();
        if (trimmedText.isEmpty()) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El comentario no puede estar vacío"));
        }

        return requireUser(userId)
            .map(validUserId -> PostComment.builder()
                .id(UUID.randomUUID().toString())
                .userId(validUserId)
                .text(trimmedText)
                .createdAt(System.currentTimeMillis())
                .build())
            .flatMap(comment -> postRepository.addComment(postId, comment)
                .onErrorMap(throwable -> translateFirestoreException(throwable, postId)))
            .then(getPost(postId));
        }

    private Post ensureCollections(Post post) {
        if (post.getLikes() == null) {
            post.setLikes(new ArrayList<>());
        }
        if (post.getComments() == null) {
            post.setComments(new ArrayList<>());
        }
        return post;
    }

    private PostResponse toResponse(Post post) {
        List<String> likes = new ArrayList<>(post.getLikes());
        List<PostCommentResponse> comments = mapComments(post.getComments());

        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .likeCount(likes.size())
                .likes(likes)
                .comments(comments)
                .build();
    }

    private List<PostCommentResponse> mapComments(List<PostComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return List.of();
        }

        return comments.stream()
                .map(comment -> PostCommentResponse.builder()
                        .id(comment.getId())
                        .userId(comment.getUserId())
                        .text(comment.getText())
                        .createdAt(comment.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    private Mono<String> requireUser(String userId) {
        if (!StringUtils.hasText(userId)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "El identificador de usuario es obligatorio"));
        }
        return Mono.just(userId.trim());
    }

    private RuntimeException translateFirestoreException(Throwable throwable, String postId) {
        if (throwable instanceof ApiException apiException) {
            StatusCode statusCode = apiException.getStatusCode();
            if (statusCode != null && statusCode.getCode() == StatusCode.Code.NOT_FOUND) {
                return new ResponseStatusException(HttpStatus.NOT_FOUND, "Post no encontrado", apiException);
            }
        }
        if (throwable instanceof RuntimeException runtime) {
            return runtime;
        }
        return new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                "Error inesperado al actualizar el post " + postId,
                throwable);
    }
}
