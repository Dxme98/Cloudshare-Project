package com.example.demo;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

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


    @DynamoDbPartitionKey
    public String getFolderId() { return folderId; }

    public void setFolderId(String folderId) { this.folderId = folderId; }
}
