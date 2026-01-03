package com.example.demo.service;

import com.example.demo.exceptions.*;
import com.example.demo.model.*;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final StorageCoreService storageCore;
    private final DynamoDbTemplate dynamoDbTemplate;

    // Ggf. höheres Limit für eingeloggte User
    private static final long MAX_USER_FOLDER_SIZE = 1024L * 1024 * 1024; // 1 GB

    // --- FOLDER MANAGEMENT ---

    public FolderInitResponse createPermanentFolder(String userId, String folderName) {
        String folderId = UUID.randomUUID().toString();

        Folder folder = Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.PERMANENT)
                .userId(userId)
                .shareToken(UUID.randomUUID().toString())
                .ownerToken(UUID.randomUUID().toString())
                .createdAt(Instant.now().toString())
                .ttl(null) // Nie löschen
                .build();

        dynamoDbTemplate.save(folder);
        return FolderMapper.toInitResponse(folder);
    }

    public List<FolderSummaryDTO> getMyFolders(String userId) {
        List<Folder> folders =  dynamoDbTemplate.query(
                QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(userId)))
                        .build(),
                Folder.class,
                "UserIndex"
        ).items().stream().toList();

        return folders.stream().map(folder -> {
            // Hier berechnen wir die Anzahl der Dateien pro Ordner, can be more efficient
            long count = storageCore.fetchFilesForFolder(folder.getFolderId()).size();

            return FolderSummaryDTO.builder()
                    .id(folder.getFolderId())
                    .name(folder.getFolderName())
                    .createdAt(folder.getCreatedAt())
                    .fileCount(count)
                    .build();
        }).toList();
    }

    /**
     * Öffnet einen spezifischen Ordner und zeigt den Inhalt an.
     * Prüft, ob der Ordner dem User gehört.
     */
    public FolderResponse openFolder(String folderId, String userId) {
        // 1. Validierung: Gehört der Ordner mir?
        Folder folder = findAndValidateOwner(folderId, userId);

        // 2. Dateien laden (via Core Service)
        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);

        // 3. Response bauen
        // Wir geben hier den OwnerToken mit zurück, falls der User
        // später im Dashboard einen Share-Link generieren will.
        return FolderMapper.toResponse(folder, folder.getOwnerToken(), files);
    }

    // --- FILE OPERATIONS ---

    public String uploadFile(String folderId, String userId, MultipartFile file) {
        Folder folder = findAndValidateOwner(folderId, userId);

        long used = storageCore.calculateCurrentFolderSize(folderId);
        if (used + file.getSize() > MAX_USER_FOLDER_SIZE) {
            throw new StorageLimitExceededException(used, MAX_USER_FOLDER_SIZE);
        }

        return storageCore.uploadPhysicalFile(folderId, file);
    }

    public S3Resource downloadFile(String folderId, String fileId, String userId) {
        // Hier könnte man erweitern: Darf User downloaden? (Owner oder "Shared With Me")
        findAndValidateOwner(folderId, userId);

        return storageCore.downloadFile(fileId);
    }

    public void deleteFile(String folderId, String fileId, String userId) {
        findAndValidateOwner(folderId, userId);

        // Sicherstellen, dass File im Folder ist
        FileMetadata meta = storageCore.getFileMetadata(fileId);
        if (!meta.getFolderId().equals(folderId)) throw new InvalidTokenException();

        storageCore.deletePhysicalFile(fileId);
    }

    public void deleteFolder(String folderId, String userId) {
        findAndValidateOwner(folderId, userId);

        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);
        files.forEach(storageCore::deletePhysicalFile);

        Folder folder = new Folder();
        folder.setFolderId(folderId);
        dynamoDbTemplate.delete(folder);
    }

    // --- HELPERS ---

    private Folder findAndValidateOwner(String folderId, String userId) {
        Folder f = dynamoDbTemplate.load(Key.builder().partitionValue(folderId).build(), Folder.class);
        if (f == null) throw new FolderNotFoundException(folderId);

        if (!userId.equals(f.getUserId())) {
            throw new AccessDeniedException("Dieser Ordner gehört dir nicht.");
        }
        return f;
    }
}