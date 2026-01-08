package com.example.demo.repository;

import com.example.demo.exceptions.custom.FileMetadataNotFoundException;
import com.example.demo.entity.FileMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
@Slf4j
public class FileMetadataRepository {
    private final DynamoDbTable<FileMetadata> table;

    public FileMetadataRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("FileMetadata", TableSchema.fromBean(FileMetadata.class));
    }

    public FileMetadata save(FileMetadata metadata) {
        table.putItem(metadata);
        return metadata;
    }

    public void delete(FileMetadata metadata) {
        table.deleteItem(metadata);
    }

    public FileMetadata findById(String fileId) {
        FileMetadata metadata = table.getItem(Key.builder().partitionValue(fileId).build());
        if(metadata == null) throw new FileMetadataNotFoundException(fileId);
        return metadata;
    }

    public List<FileMetadata> findAllByFolderId(String folderId) {
        return table.index("FolderIndex")
                .query(r -> r.queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(folderId))))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    // Aggregate-Query für Performance
    public long sumFileSizesByFolderId(String folderId) {
        return findAllByFolderId(folderId).stream()
                .mapToLong(FileMetadata::getFileSize)
                .sum();
    }
}
