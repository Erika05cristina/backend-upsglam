package com.example.post_service.service;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import com.example.post_service.client.UserServiceClient;
import com.example.post_service.dto.CreateCommentRequest;
import com.example.post_service.dto.CreatePostRequest;
import com.example.post_service.dto.CudaMetadataDto;
import com.example.post_service.dto.PostCommentResponse;
import com.example.post_service.dto.PostResponse;
import com.example.post_service.dto.UpdatePostRequest;
import com.example.post_service.dto.UserProfile;
import com.example.post_service.model.CudaMetadata;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Service
@RequiredArgsConstructor
public class PostService {

    private final PostRepository postRepository;
    private final UserServiceClient userServiceClient;

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
                .cudaMetadata(toCudaMetadata(request.getCudaMetadata()))
                .build())
            .flatMap(postRepository::save)
            .map(this::ensureCollections)
            .flatMap(this::toResponseWithAuthor);
    }

    public Mono<PostResponse> getPost(String id) {
        return postRepository.findById(id)
                .map(this::ensureCollections)
                .flatMap(this::toResponseWithAuthor)
                .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post no encontrado")));
    }

    public Mono<List<PostResponse>> getAllPosts() {
        return postRepository.findAll()
                .defaultIfEmpty(List.of())
                .flatMap(posts -> Flux.fromIterable(posts)
                        .map(this::ensureCollections)
                        .flatMap(this::toResponseWithAuthor)
                        .collectList());
    }

    public Mono<List<PostResponse>> getPostsByUser(String userId) {
        return requireUser(userId)
                .flatMap(postRepository::findByUserId)
                .defaultIfEmpty(List.of())
                .flatMap(posts -> Flux.fromIterable(posts)
                        .map(this::ensureCollections)
                        .flatMap(this::toResponseWithAuthor)
                        .collectList());
    }

    public Mono<List<PostResponse>> getFeed(String userId) {
        return requireUser(userId)
                .flatMap(validUserId -> userServiceClient.getFollowingIds(validUserId)
                        .map(following -> {
                            List<String> ids = new ArrayList<>(following);
                            if (!ids.contains(validUserId)) {
                                ids.add(validUserId);
                            }
                            return ids;
                        })
                        .flatMap(ids -> {
                            if (ids.isEmpty()) {
                                return Mono.just(List.of());
                            }

                            return Flux.fromIterable(ids)
                                    .distinct()
                                    .flatMapSequential(postRepository::findByUserId)
                                    .flatMapIterable(list -> list)
                                    .map(this::ensureCollections)
                                    .collectList()
                                    .flatMap(posts -> {
                                        if (posts.isEmpty()) {
                                            return Mono.just(List.of());
                                        }

                                        List<Post> sorted = new ArrayList<>(posts);
                                        sorted.sort((a, b) -> Long.compare(b.getCreatedAt(), a.getCreatedAt()));

                                        List<Post> limited = sorted.size() > 50
                                                ? new ArrayList<>(sorted.subList(0, 50))
                                                : sorted;

                                        return Flux.fromIterable(limited)
                                                .flatMapSequential(this::toResponseWithAuthor)
                                                .collectList();
                                    });
                        }));
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

    public Mono<PostResponse> updatePost(String postId, String userId, UpdatePostRequest request) {
        if (request == null || (request.getContent() == null && request.getImageUrl() == null && request.getCudaMetadata() == null)) {
            return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nada que actualizar"));
        }

        return requireUser(userId)
                .flatMap(validUserId -> postRepository.findById(postId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post no encontrado")))
                        .flatMap(existing -> {
                            if (!validUserId.equals(existing.getUserId())) {
                                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes editar este post"));
                            }

                            if (request.getContent() != null) {
                                String content = request.getContent().trim();
                                existing.setContent(StringUtils.hasText(content) ? content : null);
                            }

                            if (request.getImageUrl() != null) {
                                if (!StringUtils.hasText(request.getImageUrl())) {
                                    return Mono.error(new ResponseStatusException(HttpStatus.BAD_REQUEST, "imageUrl no puede estar vacío"));
                                }
                                existing.setImageUrl(request.getImageUrl().trim());
                            }

                            if (request.getCudaMetadata() != null) {
                                existing.setCudaMetadata(toCudaMetadata(request.getCudaMetadata()));
                            }

                            return postRepository.replace(existing)
                                    .map(this::ensureCollections)
                                    .flatMap(this::toResponseWithAuthor);
                        }));
    }

    public Mono<Void> deletePost(String postId, String userId) {
        return requireUser(userId)
                .flatMap(validUserId -> postRepository.findById(postId)
                        .switchIfEmpty(Mono.error(new ResponseStatusException(HttpStatus.NOT_FOUND, "Post no encontrado")))
                        .flatMap(existing -> {
                            if (!validUserId.equals(existing.getUserId())) {
                                return Mono.error(new ResponseStatusException(HttpStatus.FORBIDDEN, "No puedes eliminar este post"));
                            }
                            return postRepository.deleteById(postId);
                        }));
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

    private Mono<PostResponse> toResponseWithAuthor(Post post) {
        Mono<UserProfile> authorMono = userServiceClient.getUserProfile(post.getUserId())
                .defaultIfEmpty(UserProfile.fallback(post.getUserId()));

        Mono<List<PostCommentResponse>> commentsMono = mapCommentsWithProfiles(post.getComments());

        return Mono.zip(authorMono, commentsMono)
                .map(tuple -> buildResponse(post, tuple.getT1(), tuple.getT2()));
    }

    private PostResponse buildResponse(Post post, UserProfile profile, List<PostCommentResponse> comments) {
        List<String> likes = new ArrayList<>(post.getLikes());

        return PostResponse.builder()
                .id(post.getId())
                .userId(post.getUserId())
                .authorName(profile.getName())
                .authorAvatarUrl(profile.getAvatarUrl())
                .content(post.getContent())
                .imageUrl(post.getImageUrl())
                .createdAt(post.getCreatedAt())
                .likeCount(likes.size())
                .likes(likes)
                .comments(comments)
                .cudaMetadata(toCudaMetadataDto(post.getCudaMetadata()))
                .build();
    }

    private Mono<List<PostCommentResponse>> mapCommentsWithProfiles(List<PostComment> comments) {
        if (comments == null || comments.isEmpty()) {
            return Mono.just(List.of());
        }

        return Flux.fromIterable(comments)
                .flatMap(comment -> userServiceClient.getUserProfile(comment.getUserId())
                        .defaultIfEmpty(UserProfile.fallback(comment.getUserId()))
                        .map(profile -> PostCommentResponse.builder()
                                .id(comment.getId())
                                .userId(comment.getUserId())
                                .authorName(profile.getName())
                                .authorAvatarUrl(profile.getAvatarUrl())
                                .text(comment.getText())
                                .createdAt(comment.getCreatedAt())
                                .build()))
                .collectList();
    }

    private CudaMetadata toCudaMetadata(CudaMetadataDto dto) {
        if (dto == null) {
            return null;
        }

        return CudaMetadata.builder()
                .filterType(trimToNull(dto.getFilterType()))
                .kernelSize(dto.getKernelSize())
                .width(dto.getWidth())
                .height(dto.getHeight())
                .gpuTimeMs(dto.getGpuTimeMs())
                .blocksX(dto.getBlocksX())
                .blocksY(dto.getBlocksY())
                .threadsX(dto.getThreadsX())
                .threadsY(dto.getThreadsY())
                .threadsPerBlock(dto.getThreadsPerBlock())
                .build();
    }

    private CudaMetadataDto toCudaMetadataDto(CudaMetadata metadata) {
        if (metadata == null) {
            return null;
        }

        return CudaMetadataDto.builder()
                .filterType(metadata.getFilterType())
                .kernelSize(metadata.getKernelSize())
                .width(metadata.getWidth())
                .height(metadata.getHeight())
                .gpuTimeMs(metadata.getGpuTimeMs())
                .blocksX(metadata.getBlocksX())
                .blocksY(metadata.getBlocksY())
                .threadsX(metadata.getThreadsX())
                .threadsY(metadata.getThreadsY())
                .threadsPerBlock(metadata.getThreadsPerBlock())
                .build();
    }

    private String trimToNull(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
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
