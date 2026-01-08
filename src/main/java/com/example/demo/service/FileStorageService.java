package com.example.demo.service;

import com.example.demo.entity.FileMetadata;
import com.example.demo.exceptions.custom.FileUploadException;
import com.example.demo.repository.FileMetadataRepository;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;


@Service
@Slf4j
@RequiredArgsConstructor
public class FileStorageService {

    private final S3Template s3Template;
    private final FileMetadataRepository fileMetadataRepository;

    @Value("${S3_UPLOAD_BUCKET_NAME}")
    private String bucketName;

    /**
     * Speichert Datei in S3 und erstellt Metadata-Eintrag.
     */
    public FileMetadata uploadFile(String folderId, MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String s3Key = buildS3Key(folderId, fileId, file.getOriginalFilename());

        try (InputStream inputStream = file.getInputStream()) {
            s3Template.upload(bucketName, s3Key, inputStream);

            FileMetadata metadata = FileMetadata.builder()
                    .fileId(fileId)
                    .fileName(file.getOriginalFilename())
                    .s3Key(s3Key)
                    .fileSize(file.getSize())
                    .folderId(folderId)
                    .build();
            metadata.setUploadDate(); // Annahme: Entity hat diese Methode

            return fileMetadataRepository.save(metadata);

        } catch (IOException e) {
            log.error("S3 upload failed for file: {}", file.getOriginalFilename(), e);
            throw new FileUploadException("Upload fehlgeschlagen");
        }
    }

    public void deleteFile(String fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId);
        deleteFile(metadata);
    }

    public void deleteFile(FileMetadata metadata) {
        try {
            s3Template.deleteObject(bucketName, metadata.getS3Key());
            fileMetadataRepository.delete(metadata);
            log.debug("Deleted file: {}", metadata.getFileId());
        } catch (Exception e) {
            log.warn("Failed to delete file {}: {}", metadata.getFileId(), e.getMessage());
        }
    }

    public S3Resource downloadFile(String fileId) {
        FileMetadata metadata = fileMetadataRepository.findById(fileId);
        return s3Template.download(bucketName, metadata.getS3Key());
    }

    public List<FileMetadata> getFilesInFolder(String folderId) {
        return fileMetadataRepository.findAllByFolderId(folderId);
    }

    public FileMetadata getMetadata(String fileId) {
        return fileMetadataRepository.findById(fileId);
    }

    private String buildS3Key(String folderId, String fileId, String filename) {
        return String.format("uploads/%s/%s-%s", folderId, fileId, filename);
    }
}