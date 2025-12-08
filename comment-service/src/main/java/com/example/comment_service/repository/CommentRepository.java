package com.example.comment_service.repository;

import com.example.comment_service.model.Comment;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Repository
@RequiredArgsConstructor
public class CommentRepository {

    private final Firestore firestore;

    public Mono<Comment> save(Comment comment) {
        String id = UUID.randomUUID().toString();
        comment.setId(id);
        comment.setTimestamp(System.currentTimeMillis());

        ApiFuture<WriteResult> apiFuture =
                firestore.collection("comments").document(id).set(comment);

        CompletableFuture<WriteResult> completable = new CompletableFuture<>();
        apiFuture.addListener(() -> {
            try {
                completable.complete(apiFuture.get());
            } catch (Exception e) {
                completable.completeExceptionally(e);
            }
        }, Runnable::run);

        return Mono.fromFuture(completable).thenReturn(comment);
    }

    public Mono<List<Comment>> findByPostId(String postId) {
        ApiFuture<QuerySnapshot> future =
                firestore.collection("comments")
                        .whereEqualTo("postId", postId)
                        .orderBy("timestamp", Query.Direction.ASCENDING)
                        .get();

        CompletableFuture<QuerySnapshot> completable = new CompletableFuture<>();
        future.addListener(() -> {
            try {
                completable.complete(future.get());
            } catch (Exception e) {
                completable.completeExceptionally(e);
            }
        }, Runnable::run);

        return Mono.fromFuture(completable)
                .map(query -> query.toObjects(Comment.class));
    }
}
