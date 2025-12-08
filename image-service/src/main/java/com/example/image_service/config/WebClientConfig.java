package com.example.image_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient supabaseClient(
            @Value("${supabase.url}") String url) {

        return WebClient.builder()
                .baseUrl(url + "/storage/v1")
                .build();
    }
} 
