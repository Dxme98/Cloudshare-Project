package com.example.demo.entity;

import lombok.*;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbSecondaryPartitionKey;

import java.time.Instant;

@DynamoDbBean
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FileMetadata {
    private String fileId;
    private String folderId;
    private String fileName;
    private String s3Key;
    private Long fileSize;
    private String uploadDate;

    @DynamoDbPartitionKey
    public String getFileId() { return fileId; }

    @DynamoDbSecondaryPartitionKey(indexNames = "FolderIndex")
    public String getFolderId() { return folderId; }

    public void setFileId(String fileId) { this.fileId = fileId; }


    public void setUploadDate() {
        this.uploadDate = Instant.now().toString();
    }
}
