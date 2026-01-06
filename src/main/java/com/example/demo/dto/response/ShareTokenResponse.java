package com.example.demo.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ShareTokenResponse {
    String token;

    public static ShareTokenResponse create(String shareToken) {
        ShareTokenResponse response = new ShareTokenResponse();
        response.setToken(shareToken);

        return response;
    }
}
