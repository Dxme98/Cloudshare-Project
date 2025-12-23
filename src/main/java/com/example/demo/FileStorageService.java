package com.example.demo;

import io.awspring.cloud.dynamodb.DynamoDbTemplate;
import io.awspring.cloud.s3.S3Resource;
import io.awspring.cloud.s3.S3Template;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.enhanced.dynamodb.Key;

import java.io.IOException;
import java.time.Instant;
import java.util.UUID;

@Service
@Slf4j
public class FileStorageService {

    private final S3Template s3Template;
    private final String bucketName;
    private final DynamoDbTemplate dynamoDbTemplate;

    public FileStorageService(S3Template s3Template, @Value("${S3_UPLOAD_BUCKET_NAME}") String bucketName, DynamoDbTemplate dynamoDbTemplate ) {
        this.s3Template = s3Template;
        this.bucketName = bucketName;
        this.dynamoDbTemplate = dynamoDbTemplate;
    }

    public String uploadFile(MultipartFile file) {
        String fileId = UUID.randomUUID().toString();
        String s3Key = "uploads/" + fileId + "-" + file.getOriginalFilename();

        log.info("Starte Upload für Datei: {} (ID: {})", file.getOriginalFilename(), fileId);

        try {
            // S3 Upload
            s3Template.upload(bucketName, s3Key, file.getInputStream());
            log.info("S3 Upload erfolgreich: {}", s3Key);

            // Metadaten vorbereiten
            FileMetadata metadata = new FileMetadata();
            metadata.setFileId(fileId);
            metadata.setFileName(file.getOriginalFilename());
            metadata.setS3Key(s3Key);
            metadata.setFileSize(file.getSize());
            metadata.setUploadDate(Instant.now().toString());

            // DynamoDB Save
            dynamoDbTemplate.save(metadata);
            log.info("DynamoDB Metadaten gespeichert für ID: {}", fileId);

            return fileId;

        } catch (IOException e) {
            log.error("Fehler beim Lesen der Datei {}: {}", file.getOriginalFilename(), e.getMessage());
            throw new RuntimeException("Dateiupload fehlgeschlagen", e);
        } catch (Exception e) {
            log.error("Unerwarteter Cloud-Fehler bei ID {}: {}", fileId, e.getMessage());
            throw e;
        }
    }

    public S3Resource getFileResource(String fileId) {
        // 1. In der Map (DynamoDB) nachschauen, wo das Paket liegt
        FileMetadata metadata = dynamoDbTemplate.load(
                Key.builder().partitionValue(fileId).build(),
                FileMetadata.class
        );

        if (metadata == null) {
            throw new RuntimeException("Datei nicht gefunden!");
        }

        log.info("Lade Datei {} von S3 Pfad: {}", metadata.getFileName(), metadata.getS3Key());

        // 2. Die Resource von S3 holen (noch kein Download, nur die Verbindung)
        return s3Template.download(bucketName, metadata.getS3Key());
    }
}
