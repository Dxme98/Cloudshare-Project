package com.example.demo.service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

@Service
@RequiredArgsConstructor
@Slf4j
public class UserLookupService {

    private final CognitoIdentityProviderClient cognitoClient;

    @Value("${COGNITO_USERPOOL_ID}")
    private String userPoolId;

    /**
     * Sucht einen User anhand der E-Mail in Cognito.
     * Gibt die 'sub' (User ID) zurück oder wirft einen Fehler.
     */
    public String findUserIdByEmail(String email) {
        // Filter-Syntax von Cognito: email = "..."
        String filter = "email = \"" + email + "\"";

        ListUsersRequest request = ListUsersRequest.builder()
                .userPoolId(userPoolId)
                .filter(filter)
                .limit(1)
                .build();

        ListUsersResponse response = cognitoClient.listUsers(request);

        if (response.users().isEmpty()) {
            throw new IllegalArgumentException("User with email " + email + " not found.");
        }

        UserType user = response.users().get(0);
        return user.username();
    }

    public String findEmailByUserId(String userId) {
        try {
            AdminGetUserRequest request = AdminGetUserRequest.builder()
                    .userPoolId(userPoolId)
                    .username(userId)
                    .build();

            AdminGetUserResponse response = cognitoClient.adminGetUser(request);

            // Wir suchen das Attribut mit dem Namen "email" in der Liste
            return response.userAttributes().stream()
                    .filter(attr -> "email".equals(attr.name()))
                    .map(AttributeType::value)
                    .findFirst()
                    .orElse(null); // Fallback, falls User keine Email hat (unwahrscheinlich)

        } catch (UserNotFoundException e) {
            log.warn("User ID {} not found in Cognito.", userId);
            return null; // Wird vom Service dann als "Unknown User" behandelt
        } catch (CognitoIdentityProviderException e) {
            log.error("Error fetching user from Cognito: {}", e.awsErrorDetails().errorMessage());
            return null;
        }
    }
}
