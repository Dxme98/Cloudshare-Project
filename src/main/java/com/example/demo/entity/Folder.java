package com.example.demo.entity;

import com.example.demo.enums.FolderType;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;
import java.util.UUID;

@DynamoDbBean
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Folder {
    private String folderId;
    private String folderName;
    private String ownerToken;
    private String shareToken;
    private Long ttl;
    private String createdAt;

    private FolderType type;
    private String userId;


    @DynamoDbPartitionKey
    public String getFolderId() { return folderId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "UserIndex")
    public String getUserId() { return userId; }

    public void setFolderId(String folderId) { this.folderId = folderId; }

    public static Folder createPermanentFolder(String userId, String folderName) {
        String folderId = UUID.randomUUID().toString();
        String shareToken = UUID.randomUUID().toString();
        String ownerToken = UUID.randomUUID().toString();

        return  Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.PERMANENT)
                .userId(userId)
                .shareToken(shareToken)
                .ownerToken(ownerToken)
                .createdAt(Instant.now().toString())
                .ttl(null) // Nie löschen
                .build();
    }

    public String updateShareToken() {
        this.shareToken = UUID.randomUUID().toString();

        return this.shareToken;
    }


}
