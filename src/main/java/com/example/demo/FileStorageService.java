package com.example.demo;

import com.example.demo.exceptions.FolderNotFoundException;
import com.example.demo.exceptions.InvalidTokenException;
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
import java.util.Objects;
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

    public String uploadFile(String folderId, String token, MultipartFile file) {
        // 1. Sicherheit zuerst: Validierung des Owners
        validateOwnerRole(token, folderId);

        String fileId = UUID.randomUUID().toString();

        // S3-Struktur nach FolderId gruppieren
        // Dies erlaubt später ein effizientes Löschen des gesamten "Ordner"-Präfixes
        String s3Key = String.format("uploads/%s/%s-%s", folderId, fileId, file.getOriginalFilename());

        log.info("Starte Upload für Datei: {} in Folder: {}", file.getOriginalFilename(), folderId);

        // 2. Resource-Management: try-with-resources stellt sicher, dass der Stream geschlossen wird
        try (InputStream inputStream = file.getInputStream()) {

            // S3 Upload
            s3Template.upload(bucketName, s3Key, inputStream);
            log.info("S3 Upload erfolgreich: {}", s3Key);

            // 3. Metadaten-Persistenz
            FileMetadata metadata = FileMetadata.builder()
                    .fileId(fileId)
                    .fileName(file.getOriginalFilename())
                    .s3Key(s3Key)
                    .fileSize(file.getSize())
                    .uploadDate(Instant.now().toString())
                    .folderId(folderId)
                    .build();

            dynamoDbTemplate.save(metadata);
            log.info("DynamoDB Metadaten gespeichert für Datei-ID: {}", fileId);

            return fileId;

        } catch (IOException e) {
            log.error("Kritischer Fehler beim Lesen des Multipart-Streams: {}", e.getMessage());
            throw new RuntimeException("Dateiupload fehlgeschlagen: Lesefehler", e);
        } catch (Exception e) {
            log.error("Cloud-Fehler beim Verarbeiten von Datei {}: {}", fileId, e.getMessage());
            throw e;
        }
    }

    public S3Resource getFileResource(String fileId, String token) {
        // 1. Metadaten laden (wir brauchen die folderId daraus)
        FileMetadata metadata = dynamoDbTemplate.load(
                Key.builder().partitionValue(fileId).build(),
                FileMetadata.class
        );

        if (metadata == null) {
            throw new RuntimeException("Datei nicht gefunden!");
        }

        // 2. Jetzt validieren: Gehört der Token zum Ordner dieser Datei?
        validateToken(token, metadata.getFolderId());

        log.info("Lade Datei {} von S3 Pfad: {}", metadata.getFileName(), metadata.getS3Key());

        return s3Template.download(bucketName, metadata.getS3Key());
    }

    public FolderResponse initializeFolder() {
        String folderId = UUID.randomUUID().toString();
        log.info("Starting initialization for new folder with ID: {}", folderId);

        try {
            Folder folder = Folder.builder()
                    .folderId(folderId)
                    .ownerToken(UUID.randomUUID().toString())
                    .shareToken(UUID.randomUUID().toString())
                    .folderName("New Folder") // Kleiner UX-Vorteil statt leerem String
                    .build();

            dynamoDbTemplate.save(folder);

            log.info("Successfully saved folder {} to DynamoDB. OwnerToken generated.", folderId);

            // Da wir gerade initialisieren, ist der User definitiv der Owner
            return FolderMapper.toResponse(folder, folder.getOwnerToken(), null);

        } catch (Exception e) {
            log.error("Failed to initialize folder in DynamoDB: {}", e.getMessage(), e);
            throw new RuntimeException("Could not create folder. Please try again later.");
        }
    }

    public FolderResponse openFolder(String token, String folderId) {
        log.info("Attempting to open folder. ID: {}, Token: [PROTECTED]", folderId);

        // 1. Schritt: Folder direkt über Primary Key laden (Günstigste AWS-Operation)
        Folder folder = findFolder(folderId);

        // 2. Schritt: Token-Validierung
        validateToken(token, folderId);

        // 3. Schritt: Alle Dateien des Ordners über den GSI "FolderIndex" laden
        List<FileMetadata> files = fetchFilesForFolder(folderId);

        log.info("Successfully retrieved {} files for folder {}.", files.size(), folderId);

        // 4. Schritt: Mapper nutzen für die finale Response
        return FolderMapper.toResponse(folder, token, files);
    }

    private List<FileMetadata> fetchFilesForFolder(String folderId) {
        // Wir nutzen den Query-Befehl auf dem GSI der Metadata-Tabelle
        return dynamoDbTemplate.query(
                QueryEnhancedRequest.builder()
                        .queryConditional(QueryConditional.keyEqualTo(k -> k.partitionValue(folderId)))
                        .build(),
                FileMetadata.class,
                "FolderIndex"
        ).items().stream().toList();
    }

    void validateOwnerRole(String token, String folderId) {
        Folder folder = findFolder(folderId);

        if(!Objects.equals(folder.getOwnerToken(), token)) throw new InvalidTokenException();
    }

    void validateToken(String token, String folderId) {
        Folder folder = findFolder(folderId);

        if(!Objects.equals(folder.getOwnerToken(), token) && (!Objects.equals(folder.getShareToken(), token))) throw new InvalidTokenException();
    }

    Folder findFolder(String folderId) {
        Folder folder = dynamoDbTemplate.load(
                Key.builder().partitionValue(folderId).build(),
                Folder.class);

        if (folder == null) throw new FolderNotFoundException(folderId);

        return folder;
    }

}
