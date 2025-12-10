package com.example.user_service.repository;

import com.example.user_service.model.User;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.WriteResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

import java.util.concurrent.CompletableFuture;

@Repository
@RequiredArgsConstructor
public class UserRepository {

    private final Firestore firestore;

    public Mono<User> save(User user) {

        ApiFuture<WriteResult> apiFuture =
                firestore.collection("users")
                        .document(user.getId())
                        .set(user);

        CompletableFuture<WriteResult> completable = new CompletableFuture<>();

        apiFuture.addListener(() -> {
            try {
                completable.complete(apiFuture.get());
            } catch (Exception e) {
                completable.completeExceptionally(e);
            }
        }, Runnable::run);

        return Mono.fromCompletionStage(completable)
                .thenReturn(user);
    }

    public Mono<User> findById(String id) {
        ApiFuture<DocumentSnapshot> apiFuture =
                firestore.collection("users")
                        .document(id)
                        .get();

        CompletableFuture<DocumentSnapshot> completable = new CompletableFuture<>();

        apiFuture.addListener(() -> {
            try {
                completable.complete(apiFuture.get());
            } catch (Exception e) {
                completable.completeExceptionally(e);
            }
        }, Runnable::run);

        return Mono.fromCompletionStage(completable)
                .flatMap(doc -> {
                    if (!doc.exists()) {
                        return Mono.empty();
                    }
                    return Mono.justOrEmpty(doc.toObject(User.class));
                });
    }
}
