package com.example.demo.repository;

import com.example.demo.exceptions.custom.FolderNotFoundException;
import com.example.demo.entity.Folder;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import software.amazon.awssdk.services.dynamodb.model.UpdateItemRequest;

import java.util.List;
import java.util.Map;

@Repository
public class FolderRepository {

    private final DynamoDbTable<Folder> table;
    private final DynamoDbClient lowLevelClient;

    public FolderRepository(DynamoDbEnhancedClient enhancedClient, DynamoDbClient lowLevelClient) {
        this.table = enhancedClient.table("Folder", TableSchema.fromBean(Folder.class));
        this.lowLevelClient = lowLevelClient;
    }

    public void save(Folder folder) {
        table.putItem(folder);
    }

    public void delete(String folderId) {
        table.deleteItem(Key.builder().partitionValue(folderId).build());
    }

    public void delete(Folder folder) {
        table.deleteItem(folder);
    }

    public Folder findById(String folderId) {
        Folder folder = table.getItem(Key.builder().partitionValue(folderId).build());

        if(folder == null) throw new FolderNotFoundException(folderId);
        return folder;
    }

    public List<Folder> findAllByUserId(String userId) {
        return table.index("UserIndex")
                .query(r -> r.queryConditional(
                        QueryConditional.keyEqualTo(k -> k.partitionValue(userId))
                ))
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();
    }

    /**
     * Erhöht Zähler und Speicherplatz atomar (ohne vorheriges Lesen).
     */
    public void incrementFolderStats(String folderId, long fileSize) {
        updateFolderStats(folderId, 1, fileSize);
    }

    /**
     * Verringert Zähler und Speicherplatz atomar.
     */
    public void decrementFolderStats(String folderId, long fileSize) {
        updateFolderStats(folderId, -1, -fileSize);
    }

    private void updateFolderStats(String folderId, int countDelta, long sizeDelta) {
        String updateExpression = "SET fileCount = if_not_exists(fileCount, :start) + :inc, " +
                "usedStorage = if_not_exists(usedStorage, :start) + :size";

        UpdateItemRequest request = UpdateItemRequest.builder()
                .tableName("Folder")
                .key(Map.of("folderId", AttributeValue.builder().s(folderId).build()))
                .updateExpression(updateExpression)
                .expressionAttributeValues(Map.of(
                        ":inc", AttributeValue.builder().n(String.valueOf(countDelta)).build(),
                        ":size", AttributeValue.builder().n(String.valueOf(sizeDelta)).build(),
                        ":start", AttributeValue.builder().n("0").build()
                ))
                .build();

        lowLevelClient.updateItem(request);
    }
}
