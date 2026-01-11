package com.example.demo.entity;

import com.example.demo.enums.Role;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.*;

@DynamoDbBean
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Setter
@Getter
public class FolderShare {

    private String userId;
    private String folderId;
    private Role role;
    private String ownerId;
    private String folderName;


    @DynamoDbPartitionKey
    @DynamoDbAttribute("userId")
    @DynamoDbSecondarySortKey(indexNames = "gsi_folder_lookup")
    public String getUserId() {
        return userId;
    }

    @DynamoDbSortKey
    @DynamoDbAttribute("folderId")
    @DynamoDbSecondaryPartitionKey(indexNames = "gsi_folder_lookup")
    public String getFolderId() {
        return folderId;
    }


    @DynamoDbAttribute("role")
    public Role getRole() {
        return role;
    }

    @DynamoDbAttribute("ownerId")
    public String getOwnerId() {
        return ownerId;
    }

    @DynamoDbAttribute("folderName")
    public String getFolderName() {
        return folderName;
    }

    public static FolderShare create(String targetUserId, String folderId, String ownerId, String folderName, Role role) {
       return FolderShare.builder()
                .userId(targetUserId)
                .folderId(folderId)
                .ownerId(ownerId)
                .folderName(folderName)
                .role(role)
                .build();
    }
}
