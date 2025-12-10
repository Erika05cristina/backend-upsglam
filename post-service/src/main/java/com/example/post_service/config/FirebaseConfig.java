package com.example.post_service.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Bean
    public Firestore firestore(
            @Value("${firebase.credentials.location:classpath:firebase-key.json}") Resource credentialsResource
    ) throws IOException {

        GoogleCredentials credentials;
        try (InputStream stream = credentialsResource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(stream);
        }

        FirebaseOptions options = FirebaseOptions.builder()
            .setCredentials(credentials)
            .build();

        synchronized (FirebaseConfig.class) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(options);
            }
        }

        return FirestoreClient.getFirestore();
    }
}
