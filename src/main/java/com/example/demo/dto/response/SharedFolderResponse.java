package com.example.demo.dto.response;

import com.example.demo.enums.Role;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SharedFolderResponse {
    private String userId;
    private String folderId;
    private Role role;
    private String ownerId;
    private String folderName;
}
