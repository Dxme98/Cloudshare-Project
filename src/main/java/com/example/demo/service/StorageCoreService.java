package com.example.demo.service;

import com.example.demo.model.FileMetadata;
import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.Key;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryConditional;
import software.amazon.awssdk.enhanced.dynamodb.model.QueryEnhancedRequest;

import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@Slf4j
public class StorageCoreService {

    private final S3Template s3Template;
    private final String bucketName;
    private final DynamoDbTemplate dynamoDbTemplate;

    public StorageCoreService(S3Template s3Template,
                              @Value("${S3_UPLOAD_BUCKET_NAME}") String bucketName,
                              DynamoDbTemplate dynamoDbTemplate) {
        this.s3Template = s3Template;
        this.bucketName = bucketName;
        this.dynamoDbTemplate = dynamoDbTemplate;
    }

    /**
     * Lädt eine Datei physisch hoch und erstellt den DB-Eintrag.
     */
    public String uploadPhysicalFile(String folderId, MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String s3Key = String.format("uploads/%s/%s-%s", folderId, fileId, file.getOriginalFilename());

        log.info("Core: Starte Upload für Datei: {} in Folder: {}", file.getOriginalFilename(), folderId);

        try (InputStream inputStream = file.getInputStream()) {
            // S3 Upload
            s3Template.upload(bucketName, s3Key, inputStream);

            // Metadaten speichern
            FileMetadata metadata = FileMetadata.builder()
                    .fileId(fileId)
                    .fileName(file.getOriginalFilename())
                    .s3Key(s3Key)
                    .fileSize(file.getSize())
                    .uploadDate(Instant.now().toString())
                    .folderId(folderId)
                    .build();

            dynamoDbTemplate.save(metadata);
            log.info("Core: Upload erfolgreich. FileID: {}", fileId);
            return fileId;

        } catch (IOException e) {
            log.error("Core: Lesefehler beim Upload: {}", e.getMessage());
            throw new RuntimeException("Dateiupload fehlgeschlagen", e);
        }
    }

    /**
     * Löscht eine Datei physisch aus S3 und DynamoDB.
     */
    public void deletePhysicalFile(String fileId) {
        try {
            FileMetadata metadata = getFileMetadata(fileId);

            // 1. S3 Clean
            s3Template.deleteObject(bucketName, metadata.getS3Key());

            // 2. DB Clean
            dynamoDbTemplate.delete(metadata);

            log.info("Core: Datei {} erfolgreich gelöscht.", fileId);
        } catch (Exception e) {
            log.warn("Core: Fehler beim Löschen von Datei {}: {}", fileId, e.getMessage());
            // Wir werfen hier keinen Fehler, damit Batch-Löschungen nicht abbrechen
        }
    }

    /**
     * Batch-Löschung Helper: Löscht direkt anhand des Metadata-Objekts (spart einen DB-Call).
     */
    public void deletePhysicalFile(FileMetadata metadata) {
        try {
            s3Template.deleteObject(bucketName, metadata.getS3Key());
            dynamoDbTemplate.delete(metadata);
        } catch (Exception e) {
            log.warn("Core: Batch-Delete Fehler für {}: {}", metadata.getFileId(), e.getMessage());
        }
    }

    public S3Resource downloadFile(String fileId) {
        FileMetadata metadata = getFileMetadata(fileId);
        return s3Template.download(bucketName, metadata.getS3Key());
    }

    // --- Helper & Reads ---

    public FileMetadata getFileMetadata(String fileId) {
        FileMetadata metadata = dynamoDbTemplate.load(
                Key.builder().partitionValue(fileId).build(),
                FileMetadata.class
        );
        if (metadata == null) throw new RuntimeException("Datei nicht gefunden: " + fileId);
        return metadata;
    }

    public List<FileMetadata> fetchFilesForFolder(String folderId) {
        return dynamoDbTemplate.query(
                QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(folderId)))
                        .build(),
                FileMetadata.class,
                "FolderIndex"
        ).items().stream().toList();
    }

    public long calculateCurrentFolderSize(String folderId) {
        return fetchFilesForFolder(folderId).stream()
                .mapToLong(FileMetadata::getFileSize)
                .sum();
    }
}