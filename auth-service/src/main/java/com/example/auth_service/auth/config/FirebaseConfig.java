package com.example.auth_service.auth.config;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;

@Configuration
public class FirebaseConfig {

    @Value("${firebase.service-account-path:classpath:firebase-key.json}")
    private Resource serviceAccountResource;

    @PostConstruct
    public void init() throws IOException {
        GoogleCredentials credentials;
        try (InputStream inputStream = serviceAccountResource.getInputStream()) {
            credentials = GoogleCredentials.fromStream(inputStream);
        }

        FirebaseOptions options = new FirebaseOptions.Builder()
                .setCredentials(credentials)
                .build();

        if (FirebaseApp.getApps().isEmpty()) {
            FirebaseApp.initializeApp(options);
        }
    }
}
