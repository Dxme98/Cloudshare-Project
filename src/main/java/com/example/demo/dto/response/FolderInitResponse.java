package com.example.demo.dto.response;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class FolderInitResponse {
    private String folderId;
    private String ownerToken;
    private String shareToken;
}
