package com.example.demo.controller;

import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "0. Login Helper", description = "Hilfs-Endpunkt um einen Token für Swagger zu generieren")
public class AuthController {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${aws.cognito.client-id}")
    private String clientId;

    public record LoginRequest(String email, String password) {}
    public record TokenResponse(String accessToken, String idToken) {}

    @Operation(summary = "Login für Swagger Demo", description = "Gibt einen JWT Token zurück. Credentials: demo@test.de / Demo123!")
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