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


    public void setUploadDate() {
        this.uploadDate = Instant.now().toString();
    }

    public static FileMetadata createFileMetaData(String fileId, String fileName, String s3Key, Long fileSize, String folderId) {
        FileMetadata metaData =  FileMetadata.builder()
                .fileId(fileId)
                .fileName(fileName)
                .s3Key(s3Key)
                .fileSize(fileSize)
                .folderId(folderId)
                .build();

        metaData.setUploadDate();

        return metaData;
    }
}
