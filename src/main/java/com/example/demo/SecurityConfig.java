package com.example.demo;

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

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                // 1. CORS aktivieren
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                // 2. CSRF deaktivieren (nicht nötig für Stateless REST APIs)
                .csrf(AbstractHttpConfigurer::disable)

                // 3. Regeln definieren
                .authorizeHttpRequests(auth -> auth
                        // A) Die öffentlichen Endpoints (Anonymer Flow)
                        .requestMatchers("/api/folders/**", "/api/files/**").permitAll()
                        .requestMatchers("/actuator/health").permitAll()

                        // B) Der neue geschützte Bereich (kommt gleich)
                        .requestMatchers("/api/dashboard/**").authenticated()

                        // C) Alles andere blockieren
                        .anyRequest().authenticated()
                )

                // 4. Wir sind ein Resource Server und wollen JWTs validieren
                .oauth2ResourceServer(oauth2 -> oauth2.jwt(Customizer.withDefaults()));

        return http.build();
    }


    // CORS Konfiguration für Localhost Entwicklung
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
}
