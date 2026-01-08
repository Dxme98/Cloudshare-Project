package com.example.demo.entity;

import com.example.demo.enums.FolderType;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
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


    private Long fileCount;
    private Long usedStorage;
    private Long maxStorage;


    @DynamoDbPartitionKey
    public String getFolderId() { return folderId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "UserIndex")
    public String getUserId() { return userId; }

    public void setFolderId(String folderId) { this.folderId = folderId; }

    public static Folder createPermanentFolder(String userId, String folderName) {
        String folderId = UUID.randomUUID().toString();
        String shareToken = UUID.randomUUID().toString();

        return  Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.PERMANENT)
                .userId(userId)
                .shareToken(shareToken)
                .ownerToken(null)
                .maxStorage(1024L * 1024 * 1024) // 1 GB
                .createdAt(Instant.now().toString())
                .ttl(null) // Nie löschen
                .build();
    }

    public static Folder createTemporaryFolder( String folderName) {
        String folderId = UUID.randomUUID().toString();
        String shareToken = UUID.randomUUID().toString();
        String ownerToken = UUID.randomUUID().toString();

        return  Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.TEMPORARY)
                .shareToken(shareToken)
                .ownerToken(ownerToken)
                .maxStorage(500L * 1024 * 1024) // 500 MB
                .createdAt(Instant.now().toString())
                .ttl(Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond()) // Nach 24h löschen
                .build();
    }

    public String updateShareToken() {
        this.shareToken = UUID.randomUUID().toString();

        return this.shareToken;
    }


}
