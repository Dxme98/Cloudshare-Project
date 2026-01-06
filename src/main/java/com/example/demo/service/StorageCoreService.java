package com.example.demo.service;

import com.example.demo.model.FileMetadata;
import com.example.demo.repository.FileMetadataRepository;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

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
    private final FileMetadataRepository fileMetadataRepository;

    public StorageCoreService(S3Template s3Template,
                              @Value("${S3_UPLOAD_BUCKET_NAME}") String bucketName,
                              FileMetadataRepository fileMetadataRepository) {
        this.s3Template = s3Template;
        this.bucketName = bucketName;
        this.fileMetadataRepository = fileMetadataRepository;
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

            fileMetadataRepository.save(metadata);
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
            FileMetadata metadata = fileMetadataRepository.findById(fileId);

            // 1. S3 Clean
            s3Template.deleteObject(bucketName, metadata.getS3Key());

            // 2. DB Clean
            fileMetadataRepository.delete(metadata);

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
            fileMetadataRepository.delete(metadata);
        } catch (Exception e) {
            log.warn("Core: Batch-Delete Fehler für {}: {}", metadata.getFileId(), e.getMessage());
        }
    }

    public S3Resource downloadFile(String fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId);
        return s3Template.download(bucketName, metadata.getS3Key());
    }

    public List<FileMetadata> fetchFilesForFolder(String folderId) {
        return fileMetadataRepository.findAllByFolderId(folderId);
    }

    public FileMetadata getFileMetadata(String fileId) {
        return fileMetadataRepository.findById(fileId);
    }

    // --- Helper & Reads ---
    public long calculateCurrentFolderSize(String folderId) {
        List<FileMetadata> metadata = fileMetadataRepository.findAllByFolderId(folderId);
        return metadata.stream().mapToLong(FileMetadata::getFileSize).sum();
    }


    public int getFileCount(String folderId) {
        return fetchFilesForFolder(folderId).size();
    }
}