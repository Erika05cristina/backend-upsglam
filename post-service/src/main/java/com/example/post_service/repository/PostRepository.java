package com.example.post_service.repository;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutionException;

import com.example.post_service.model.Post;
import com.example.post_service.model.PostComment;
import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.FieldValue;
import com.google.cloud.firestore.Query;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteResult;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

@Repository
@RequiredArgsConstructor
public class PostRepository {

    private final Firestore firestore;

    public Mono<Post> save(Post post) {
        String id = UUID.randomUUID().toString();
        post.setId(id);

        ApiFuture<WriteResult> write = postsCollection()
                .document(id)
                .set(post);

        return monoFromApiFuture(write).thenReturn(post);
    }

    public Mono<Post> findById(String id) {
        ApiFuture<DocumentSnapshot> read = postsCollection()
                .document(id)
                .get();

        return monoFromApiFuture(read)
                .flatMap(snapshot -> {
                    if (!snapshot.exists()) {
                        return Mono.empty();
                    }
                    Post post = snapshot.toObject(Post.class);
                    return Mono.justOrEmpty(post);
                });
    }

    public Mono<List<Post>> findAll() {
        ApiFuture<QuerySnapshot> read = postsCollection()
                .orderBy("createdAt", Query.Direction.DESCENDING)
                .get();

        return monoFromApiFuture(read)
                .map(query -> query.toObjects(Post.class));
    }

        public Mono<List<Post>> findByUserId(String userId) {
        ApiFuture<QuerySnapshot> read = postsCollection()
            .whereEqualTo("userId", userId)
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .get();

        return monoFromApiFuture(read)
            .map(query -> query.toObjects(Post.class));
        }

            public Mono<Post> replace(Post post) {
            ApiFuture<WriteResult> write = postsCollection()
                .document(post.getId())
                .set(post);

            return monoFromApiFuture(write).thenReturn(post);
            }

            public Mono<Void> deleteById(String postId) {
            ApiFuture<WriteResult> write = postsCollection()
                .document(postId)
                .delete();

            return monoFromApiFuture(write).then();
            }

    public Mono<Void> addLike(String postId, String userId) {
        ApiFuture<WriteResult> update = postsCollection()
                .document(postId)
                .update("likes", FieldValue.arrayUnion(userId));

        return monoFromApiFuture(update).then();
    }

    public Mono<Void> removeLike(String postId, String userId) {
        ApiFuture<WriteResult> update = postsCollection()
                .document(postId)
                .update("likes", FieldValue.arrayRemove(userId));

        return monoFromApiFuture(update).then();
    }

    public Mono<Void> addComment(String postId, PostComment comment) {
        Map<String, Object> commentPayload = Map.of(
                "id", comment.getId(),
                "userId", comment.getUserId(),
                "text", comment.getText(),
                "createdAt", comment.getCreatedAt()
        );

        ApiFuture<WriteResult> update = postsCollection()
                .document(postId)
                .update("comments", FieldValue.arrayUnion(commentPayload));

        return monoFromApiFuture(update).then();
    }

    private CollectionReference postsCollection() {
        return firestore.collection("posts");
    }

    private <T> Mono<T> monoFromApiFuture(ApiFuture<T> future) {
        return Mono.<T>create(sink -> future.addListener(() -> {
                    try {
                        sink.success(future.get());
                    } catch (InterruptedException ex) {
                        Thread.currentThread().interrupt();
                        sink.error(ex);
                    } catch (ExecutionException ex) {
                        Throwable cause = ex.getCause();
                        sink.error(cause != null ? cause : ex);
                    }
                }, command -> Schedulers.boundedElastic().schedule(command)))
                .subscribeOn(Schedulers.boundedElastic());
    }
}
