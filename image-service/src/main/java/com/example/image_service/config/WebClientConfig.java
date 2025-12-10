package com.example.image_service.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientConfig {

    @Bean
    public WebClient supabaseClient(@Value("${supabase.url}") String url) {
        return WebClient.builder()
                .baseUrl(url + "/storage/v1")
                .build();
    }

    @Bean
    public WebClient pythonClient(@Value("${python.url}") String pythonUrl) {
        return WebClient.builder()
                .baseUrl(pythonUrl)
                .codecs(configurer ->
                        configurer
                                .defaultCodecs()
                                // por ejemplo 10 MB
                                .maxInMemorySize(10 * 1024 * 1024)
                )
                .build();
    }
}
