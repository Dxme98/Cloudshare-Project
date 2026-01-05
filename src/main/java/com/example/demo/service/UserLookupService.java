package com.example.demo.service;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersRequest;
import software.amazon.awssdk.services.cognitoidentityprovider.model.ListUsersResponse;
import software.amazon.awssdk.services.cognitoidentityprovider.model.UserType;

@Service
@RequiredArgsConstructor
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
}
