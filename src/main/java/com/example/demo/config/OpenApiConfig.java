package com.example.demo.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        String description = """
                ## CloudShare API Architecture
                
                Diese API ist das Backend einer modernen, hochverfügbaren File-Sharing-Plattform.
                Die Architektur folgt dem **Cloud-Native** Ansatz und wurde vollständig via **Terraform (IaC)** provisioniert.
                
                ### Technology Stack
                * **Backend:** Spring Boot 3 (Java 17+) mit Actuator & Security.
                * **Compute:** Serverless Container-Orchestrierung via **AWS ECS Fargate**.
                * **Storage:** Hochperformante NoSQL-Datenbank (**DynamoDB**) und Object Storage (**S3**).
                * **Event-Driven:** Asynchrone Prozesse (Cleanup) via **DynamoDB Streams & AWS Lambda (Python)**.
                * **Security:** Identitätsmanagement via **AWS Cognito**, abgesichert durch **VPC Endpoints** (Private Networking) und OIDC für GitHub Actions.
                
                ---
                
                ### Live Testing (Dashboard)
                
                Um die geschützten Endpunkte zu testen (Login erforderlich), nutzen Sie bitte den **Login-Helper**:
                
                1.  Öffnen Sie unten den Controller **`0. Login Helper`**.
                2.  Generieren Sie einen Token via `/api/auth/login` mit den Demo-Daten:
                    * **Email:** `demo@test.de`
                    * **Password:** `Demo123!`
                3.  Klicken Sie oben rechts auf **`Authorize`** und fügen Sie den Token ein.
                
                Viel Spaß!
                """;

        return new OpenAPI()
                .info(new Info()
                        .title("CloudShare API Documentation")
                        .version("1.0.0")
                        .description(description))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                        ));
    }
}