package com.example.demo;

import lombok.Getter;
import lombok.Setter;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbBean;
import software.amazon.awssdk.enhanced.dynamodb.mapper.annotations.DynamoDbPartitionKey;

@DynamoDbBean
@Getter
@Setter
public class FileMetadata {
    private String fileId;
    private String fileName;
    private String s3Key;
    private Long fileSize;
    private String uploadDate;

    @DynamoDbPartitionKey
    public String getFileId() { return fileId; }

    public void setFileId(String fileId) { this.fileId = fileId; }
}
