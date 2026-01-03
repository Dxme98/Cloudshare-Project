package com.example.demo.model;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

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
    @Getter(onMethod_ = {@DynamoDbSecondaryPartitionKey(indexNames = "UserIndex")})
    private String userId;


    @DynamoDbPartitionKey
    public String getFolderId() { return folderId; }

    public void setFolderId(String folderId) { this.folderId = folderId; }
}
