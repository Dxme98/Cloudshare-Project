package com.example.demo.entity;

import com.example.demo.enums.FolderType;
import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Duration;
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

    public static Folder createPermanentFolder(String userId, String folderName) {
        String folderId = UUID.randomUUID().toString();
        String shareToken = UUID.randomUUID().toString();

        return Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.PERMANENT)
                .userId(userId)
                .shareToken(shareToken)
                .ownerToken(null)
                .maxStorage(FolderType.PERMANENT.getDefaultStorageLimit())
                .createdAt(Instant.now().toString())
                .ttl(calculateTtl(FolderType.PERMANENT))
                .usedStorage(0L)
                .fileCount(0L)
                .build();
    }

    public static Folder createTemporaryFolder(String folderName) {
        String folderId = UUID.randomUUID().toString();
        String shareToken = UUID.randomUUID().toString();
        String ownerToken = UUID.randomUUID().toString();

        return Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.TEMPORARY)
                .shareToken(shareToken)
                .ownerToken(ownerToken)
                .maxStorage(FolderType.TEMPORARY.getDefaultStorageLimit())
                .createdAt(Instant.now().toString())
                .ttl(calculateTtl(FolderType.TEMPORARY))
                .usedStorage(0L)
                .fileCount(0L)
                .build();
    }

    public String updateShareToken() {
        this.shareToken = UUID.randomUUID().toString();

        return this.shareToken;
    }

    private static Long calculateTtl(FolderType type) {
        Duration duration = type.getDefaultTtlDuration();
        if (duration == null) {
            return null; // Permanent
        }
        return Instant.now().plus(duration).getEpochSecond();
    }

}
