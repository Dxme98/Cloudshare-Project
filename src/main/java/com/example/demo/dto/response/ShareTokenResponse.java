package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "ShareTokenResponse", description = "Wrapper für einen generierten Share-Token")
public class ShareTokenResponse {

    @Schema(description = "Der generierte JWT Token für den Zugriff", example = "eyJhbGciOiJIUzI1NiIsIn...")
    String token;

    public static ShareTokenResponse create(String shareToken) {
        ShareTokenResponse response = new ShareTokenResponse();
        response.setToken(shareToken);
        return response;
    }
}