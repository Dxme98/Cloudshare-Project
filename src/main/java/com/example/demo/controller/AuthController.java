package com.example.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.AuthFlowType;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.InitiateAuthResponse;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "0. Login Helper", description = "Start hier! Holt den Token für die geschützten Endpoints.")
public class AuthController {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${aws.cognito.client-id}")
    private String clientId;


    @Schema(description = "Login-Daten für den Demo-User")
    public record LoginRequest(
            @Schema(description = "Email des Recruiters", example = "demo@test.de")
            String email,

            @Schema(description = "Passwort", example = "Demo123!")
            String password
    ) {}

    @Schema(description = "Antwort mit den Schlüsseln (Tokens)")
    public record TokenResponse(
            @Schema(description = "Der Access Token (Ignorieren)")
            String accessToken,

            @Schema(description = "Kopiere diesen Token in den 'Authorize' Button oben rechts (Format: Bearer <token>)")
            String idToken
    ) {}

    // --- Endpoints ---

    @Operation(
            summary = "Login für Demo-User",
            description = """
                    Gibt den JWT Token zurück.\s
                    
                    **Anleitung:**
                    1. Klicke auf 'Try it out'.
                    2. Die Credentials sind bereits vorausgefüllt (einfach 'Execute' drücken).
                    3. Kopiere den `idToken` aus der Antwort.
                    4. Scrolle nach ganz oben zum grünen 'Authorize' Button.
                    5. Füge den Token dort ein."""
    )
    @ApiResponse(
            responseCode = "200",
            description = "Login erfolgreich - Hier ist dein Token",
            content = @Content(schema = @Schema(implementation = TokenResponse.class))
    )
    @ApiResponse(
            responseCode = "401",
            description = "Login fehlgeschlagen (Falsches Passwort?)"
    )
    @PostMapping("/login")
    public ResponseEntity<TokenResponse> login(@RequestBody LoginRequest request) {

        Map<String, String> authParams = Map.of(
                "USERNAME", request.email(),
                "PASSWORD", request.password()
        );

        InitiateAuthRequest authRequest = InitiateAuthRequest.builder()
                .clientId(clientId)
                .authFlow(AuthFlowType.USER_PASSWORD_AUTH)
                .authParameters(authParams)
                .build();

        try {
            InitiateAuthResponse response = cognitoClient.initiateAuth(authRequest);
            String accessToken = response.authenticationResult().accessToken();
            String idToken = response.authenticationResult().idToken();

            return ResponseEntity.ok(new TokenResponse(accessToken, idToken));

        } catch (Exception e) {
            return ResponseEntity.status(401).build();
        }
    }
}