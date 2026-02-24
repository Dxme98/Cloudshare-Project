package com.example.demo.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@EnableWebSecurity
@Configuration
public class SecurityConfig {
    @Value("${ALLOWED_ORIGINS:}")
    private List<String> cloudOrigins;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // CORS aktivieren
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // CSRF deaktivieren (nicht nötig für Stateless REST APIs)
                .csrf(AbstractHttpConfigurer::disable)

                .authorizeHttpRequests(auth -> auth
                        // Die öffentlichen Endpoints (Anonymer Flow)
                        .requestMatchers("/api/folders/**", "/api/files/**").permitAll() // application
                        .requestMatchers("/actuator/health").permitAll() // actutator
                        .requestMatchers("/api/auth/login", "/swagger-ui/**", "/v3/api-docs/**").permitAll() // swagger

                        // Der Auth Bereich (nach Anmeldung)
                        .requestMatchers("/api/dashboard/**").authenticated()

                        // C) Alles andere blockieren
                        .anyRequest().authenticated()
                )

                // 4. Backend fungiert als Resource Server
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }


    // CORS Konfiguration für Localhost Entwicklung
    /**
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        // Erlaube React Localhost
        configuration.setAllowedOrigins(List.of("http://localhost:5173"));

        // Erlaube alle Methoden (GET, POST, DELETE, PUT, OPTIONS)
        configuration.setAllowedMethods(List.of("*"));

        // Erlaube alle Header (besonders wichtig: "Authorization")
        configuration.setAllowedHeaders(List.of("*"));

        // Erlaube Credentials (falls nötig, hier eher optional da wir Bearer Token nutzen)
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
    */

    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();

        List<String> allAllowedOrigins = new java.util.ArrayList<>();
        allAllowedOrigins.add("http://localhost:5173");

        if (cloudOrigins != null && !cloudOrigins.isEmpty()) {
            allAllowedOrigins.addAll(cloudOrigins);
        }

        configuration.setAllowedOrigins(allAllowedOrigins);

        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }


}
