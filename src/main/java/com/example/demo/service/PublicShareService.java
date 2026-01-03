package com.example.demo.service;

import com.example.demo.exceptions.*;
import com.example.demo.model.*;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PublicShareService {

    private final StorageCoreService storageCore;
    private final DynamoDbTemplate dynamoDbTemplate;

    private static final long MAX_FOLDER_SIZE_BYTES = 500 * 1024 * 1024; // 500 MB Limit

    // --- FOLDER MANAGEMENT ---

    public FolderInitResponse initializeFolder() {
        String folderId = UUID.randomUUID().toString();
        long expirationTime = Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond();

        Folder folder = Folder.builder()
                .folderId(folderId)
                .ownerToken(UUID.randomUUID().toString())
                .shareToken(UUID.randomUUID().toString())
                .ttl(expirationTime)
                .folderName("Temporary Folder")
                .type(FolderType.TEMPORARY)
                .build();

        dynamoDbTemplate.save(folder);
        return FolderMapper.toInitResponse(folder);
    }

    public FolderResponse openFolder(String token, String folderId) {
        Folder folder = findFolder(folderId);
        validateToken(folder, token); // Check: Owner oder Share Token

        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);
        return FolderMapper.toResponse(folder, token, files);
    }

    // --- FILE OPERATIONS ---

    public String uploadFileWithToken(String folderId, String token, MultipartFile file) {
        Folder folder = findFolder(folderId);
        validateOwnerToken(folder, token); // Nur Owner darf uploaden

        // Quota Check
        long used = storageCore.calculateCurrentFolderSize(folderId);
        if (used + file.getSize() > MAX_FOLDER_SIZE_BYTES) {
            throw new StorageLimitExceededException(used, MAX_FOLDER_SIZE_BYTES);
        }

        return storageCore.uploadPhysicalFile(folderId, file);
    }

    public S3Resource downloadFile(String folderId, String fileId, String token) {
        Folder folder = findFolder(folderId);
        validateToken(folder, token); // Jeder mit Token darf laden

        // Prüfen ob File wirklich zum Folder gehört (Sicherheit!)
        FileMetadata metadata = storageCore.getFileMetadata(fileId);
        if (!metadata.getFolderId().equals(folderId)) {
            throw new InvalidTokenException();
        }

        return storageCore.downloadFile(fileId);
    }

    public void deleteFileWithToken(String folderId, String fileId, String token) {
        Folder folder = findFolder(folderId);
        validateOwnerToken(folder, token); // Nur Owner darf Files löschen

        FileMetadata metadata = storageCore.getFileMetadata(fileId);
        if (!metadata.getFolderId().equals(folderId)) throw new InvalidTokenException();

        storageCore.deletePhysicalFile(fileId);
    }

    // --- PRIVATE HELPERS ---

    private Folder findFolder(String folderId) {
        Folder f = dynamoDbTemplate.load(Key.builder().partitionValue(folderId).build(), Folder.class);
        if (f == null) throw new FolderNotFoundException(folderId);
        return f;
    }

    private void validateToken(Folder folder, String token) {
        if (!Objects.equals(folder.getOwnerToken(), token) &&
                !Objects.equals(folder.getShareToken(), token)) {
            throw new InvalidTokenException();
        }
    }

    private void validateOwnerToken(Folder folder, String token) {
        if (!Objects.equals(folder.getOwnerToken(), token)) {
            throw new InvalidTokenException();
        }
    }
}