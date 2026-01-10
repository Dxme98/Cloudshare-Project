package com.example.demo.ServiceIntegrationTests;

import com.example.demo.service.UserLookupService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.awscore.exception.AwsErrorDetails;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;
import software.amazon.awssdk.services.cognitoidentityprovider.model.*;

import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserLookupServiceTest {

    @Mock
    private CognitoIdentityProviderClient cognitoClient;

    @InjectMocks
    private UserLookupService userLookupService;

    private static final String POOL_ID = "us-east-1_123456789";

    @BeforeEach
    void setup() {
        ReflectionTestUtils.setField(userLookupService, "userPoolId", POOL_ID);
    }

    // ==================== findUserIdByEmail Tests ====================

    @Test
    @DisplayName("Should return username if user exists")
    void shouldReturnUsernameIfUserExists() {
        // Given
        String email = "test@example.com";
        String expectedUsername = "user-uuid-123";

        // Mock AWS Response
        UserType mockUser = UserType.builder().username(expectedUsername).build();
        ListUsersResponse response = ListUsersResponse.builder().users(mockUser).build();

        when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(response);

        // When
        String result = userLookupService.findUserIdByEmail(email);

        // Then
        assertThat(result).isEqualTo(expectedUsername);

        ArgumentCaptor<ListUsersRequest> captor = ArgumentCaptor.forClass(ListUsersRequest.class);
        verify(cognitoClient).listUsers(captor.capture());

        ListUsersRequest capturedRequest = captor.getValue();
        assertThat(capturedRequest.userPoolId()).isEqualTo(POOL_ID);
        assertThat(capturedRequest.filter()).isEqualTo("email = \"test@example.com\"");
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException if user not found")
    void shouldThrowIfUserNotFound() {
        // Given
        String email = "ghost@example.com";

        // Mock Empty Response
        ListUsersResponse emptyResponse = ListUsersResponse.builder().users(Collections.emptyList()).build();
        when(cognitoClient.listUsers(any(ListUsersRequest.class))).thenReturn(emptyResponse);

        // When & Then
        assertThatThrownBy(() -> userLookupService.findUserIdByEmail(email))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not found");
    }

    // ==================== findEmailByUserId Tests ====================

    @Test
    @DisplayName("Should extract email attribute correctly")
    void shouldExtractEmailAttribute() {
        // Given
        String userId = "user-123";
        String expectedEmail = "found@example.com";

        // Mock AWS Response mit Attributen
        AttributeType emailAttr = AttributeType.builder().name("email").value(expectedEmail).build();
        AttributeType otherAttr = AttributeType.builder().name("phone").value("12345").build();

        AdminGetUserResponse response = AdminGetUserResponse.builder()
                .userAttributes(otherAttr, emailAttr)
                .build();

        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class))).thenReturn(response);

        // When
        String result = userLookupService.findEmailByUserId(userId);

        // Then
        assertThat(result).isEqualTo(expectedEmail);
    }

    @Test
    @DisplayName("Should return null if UserNotFoundException occurs")
    void shouldReturnNullOnUserNotFound() {
        // Given
        String userId = "unknown-id";

        // Mock Exception
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(UserNotFoundException.builder().message("User not found").build());

        // When
        String result = userLookupService.findEmailByUserId(userId);

        // Then
        assertThat(result).isNull();
    }

    @Test
    @DisplayName("Should return null on generic Cognito Error")
    void shouldReturnNullOnCognitoError() {
        // Given
        String userId = "error-id";

        // Mock Generic AWS Exception
        AwsErrorDetails details = AwsErrorDetails.builder().errorMessage("Server Error").build();
        when(cognitoClient.adminGetUser(any(AdminGetUserRequest.class)))
                .thenThrow(CognitoIdentityProviderException.builder().awsErrorDetails(details).build());

        // When
        String result = userLookupService.findEmailByUserId(userId);

        // Then
        assertThat(result).isNull();
    }
}