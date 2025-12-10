package com.example.api_gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.reactive.CorsConfigurationSource;
import org.springframework.web.cors.reactive.UrlBasedCorsConfigurationSource;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource())) // ← ACTIVAMOS CORS
                .authorizeExchange(exchange -> exchange
                        .pathMatchers("/**").permitAll()
                )
                .build();
    }

    /**
     * CONFIGURACIÓN GLOBAL DE CORS PARA EL API GATEWAY
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        config.addAllowedOriginPattern("*");   // permítelo todo en modo dev
        config.addAllowedMethod("*");          // GET, POST, PUT, DELETE, OPTIONS
        config.addAllowedHeader("*");          // Content-Type, Authorization, etc.
        config.setAllowCredentials(false);     // Desde web, credenciales puede causar bloqueos

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
