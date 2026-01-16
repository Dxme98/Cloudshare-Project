package com.example.demo.repository;

import com.example.demo.entity.FolderShare;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.PageIterable;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.Optional;

@Repository
public class FolderShareRepository {

    private final DynamoDbTable<FolderShare> table;

    public FolderShareRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("FolderShare", TableSchema.fromBean(FolderShare.class));
    }

    public void save(FolderShare share) {
        table.putItem(share);
    }

    public void deleteShare(String targetUserId, String folderId) {
        Key key = Key.builder()
                .partitionValue(targetUserId)
                .sortValue(folderId)
                .build();

        table.deleteItem(key);
    }

    public PageIterable<FolderShare> findByUserId(String userId) {
        return table.query(r -> r.queryConditional(
                QueryConditional.keyEqualTo(k -> k.partitionValue(userId))
        ));
    }

    public PageIterable<FolderShare> findByFolderId(String folderId) {
        return (PageIterable<FolderShare>) table.index("gsi_folder_lookup").query(r -> r.queryConditional(
                QueryConditional.keyEqualTo(k -> k.partitionValue(folderId))
        ));
    }

    public Optional<FolderShare> findAccess(String userId, String folderId) {
        return Optional.ofNullable(table.getItem(Key.builder()
                .partitionValue(userId)
                .sortValue(folderId)
                .build()));
    }
}
