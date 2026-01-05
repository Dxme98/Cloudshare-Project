package com.example.demo.repository;

import com.example.demo.exceptions.FileMetadataNotFoundException;
import com.example.demo.model.FileMetadata;
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

    public void save(FileMetadata metadata) {
        table.putItem(metadata);
    }

    public void delete(FileMetadata metadata) {
        table.deleteItem(metadata);
    }

    public FileMetadata findById(String fileId) {
        FileMetadata metadata = table.getItem(Key.builder().partitionValue(fileId).build());

        if(metadata == null) throw new FileMetadataNotFoundException(fileId); // ex handler hinzufügen

        return metadata;
    }

    // Ersetzt den komplexen Template-Query-Code, später Pagination
    public List<FileMetadata> findAllByFolderId(String folderId) {
        return table.index("FolderIndex")
                .query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(k -> k.partitionValue(folderId))
                ))
                .stream()                                   // 1. Wir streamen die "Seiten" (Pages)
                .flatMap(page -> page.items().stream())     // 2. Wir holen aus jeder Seite die Items und machen einen großen Stream daraus
                .toList();                                  // 3. Sammeln alles in einer Liste
    }
}
