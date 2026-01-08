package com.example.demo.repository;

import com.example.demo.exceptions.custom.FolderNotFoundException;
import com.example.demo.entity.Folder;
import org.springframework.stereotype.Repository;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbTable;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.TableSchema;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;

import java.util.List;

@Repository
public class FolderRepository {

    private final DynamoDbTable<Folder> table;

    public FolderRepository(DynamoDbEnhancedClient enhancedClient) {
        this.table = enhancedClient.table("Folder", TableSchema.fromBean(Folder.class));
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
}
