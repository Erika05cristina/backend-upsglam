package com.example.auth_service.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity http) {
        return http
                .csrf(ServerHttpSecurity.CsrfSpec::disable) // Desactivar CSRF
                .authorizeExchange(exchanges -> exchanges
                        .pathMatchers("/auth/**").permitAll() // 🔥 Permitir sin login
                        .anyExchange().authenticated()
                )
                .httpBasic(ServerHttpSecurity.HttpBasicSpec::disable) // ❌ Desactiva login básico
                .formLogin(ServerHttpSecurity.FormLoginSpec::disable) // ❌ Desactiva formulario login
                .build();
    }
}
