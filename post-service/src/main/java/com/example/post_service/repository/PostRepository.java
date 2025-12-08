package com.example.post_service.repository;

import com.example.post_service.model.Post;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.UUID;

@Repository
public class PostRepository {

    private final Firestore firestore;

    public PostRepository(Firestore firestore) {
        this.firestore = firestore;
    }

    public Mono<Post> save(Post post) {
        String id = UUID.randomUUID().toString();
        post.setId(id);
        // ApiFuture to CompletableFuture
        com.google.api.core.ApiFuture<com.google.cloud.firestore.WriteResult> apiFuture =
                firestore.collection("posts").document(id).set(post);
        java.util.concurrent.CompletableFuture<com.google.cloud.firestore.WriteResult> completableFuture = new java.util.concurrent.CompletableFuture<>();
        apiFuture.addListener(() -> {
            try {
                completableFuture.complete(apiFuture.get());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        }, Runnable::run);
        return Mono.fromFuture(completableFuture).thenReturn(post);
    }

    public Mono<Post> findById(String id) {
        com.google.api.core.ApiFuture<com.google.cloud.firestore.DocumentSnapshot> apiFuture =
                firestore.collection("posts").document(id).get();
        java.util.concurrent.CompletableFuture<com.google.cloud.firestore.DocumentSnapshot> completableFuture = new java.util.concurrent.CompletableFuture<>();
        apiFuture.addListener(() -> {
            try {
                completableFuture.complete(apiFuture.get());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        }, Runnable::run);
        return Mono.fromFuture(completableFuture)
                .map(doc -> doc.exists() ? doc.toObject(Post.class) : null);
    }

    public Mono<java.util.List<Post>> findAll() {
        com.google.api.core.ApiFuture<com.google.cloud.firestore.QuerySnapshot> apiFuture =
                firestore.collection("posts").get();
        java.util.concurrent.CompletableFuture<com.google.cloud.firestore.QuerySnapshot> completableFuture = new java.util.concurrent.CompletableFuture<>();
        apiFuture.addListener(() -> {
            try {
                completableFuture.complete(apiFuture.get());
            } catch (Exception e) {
                completableFuture.completeExceptionally(e);
            }
        }, Runnable::run);
        return Mono.fromFuture(completableFuture)
                .map(query -> query.toObjects(Post.class));
    }
}
