package com.example.demo;

import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.InputStream;

@RestController
@RequestMapping("/api/folders")
@RequiredArgsConstructor
public class FileManagementController {

    private final FileStorageService fileStorageService;

    /**
     * Erstellt einen neuen anonymen Ordner und gibt FolderId + Token zurück.
     */
    @PostMapping
    public ResponseEntity<FolderInitResponse> initializeFolder() {
        FolderInitResponse folderResponse = fileStorageService.initializeFolder();
        return ResponseEntity.status(HttpStatus.CREATED).body(folderResponse);
    }

    /**
     * Öffnet einen bestehenden Ordner (liefert Metadaten und Dateiliste).
     */
    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> openFolder(
            @PathVariable String folderId,
            @RequestParam String token) {

        FolderResponse folderResponse = fileStorageService.openFolder(token, folderId);
        return ResponseEntity.ok(folderResponse);
    }

    /**
     * Löscht einen kompletten Ordner manuell.
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable String folderId,
            @RequestParam String token) {

        fileStorageService.deleteFolder(folderId, token);
        return ResponseEntity.noContent().build();
    }


    /**
     * Lädt eine Datei in einen spezifischen Ordner hoch.
     */
    @PostMapping("/{folderId}/files")
    public ResponseEntity<String> uploadFile(
            @PathVariable String folderId,
            @RequestParam String token,
            @RequestParam("file") MultipartFile file) {

        String fileId = fileStorageService.uploadFile(folderId, token, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(fileId);
    }

    /**
     * Lädt eine spezifische Datei herunter.
     * Nutzt StreamingResponseBody für ECS-Ressourcenschonung.
     */
    @GetMapping("/{folderId}/files/{fileId}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @RequestParam String token) throws IOException {

        // Resource vom Service holen (enthält S3-Stream und Metadaten)
        S3Resource resource = fileStorageService.getFileResource(fileId, token);

        String filename = resource.getFilename();
        long contentLength = resource.contentLength(); // Wichtig für den Browser-Fortschrittsbalken

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream is = resource.getInputStream()) {
                is.transferTo(outputStream);
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentLength(contentLength)
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }

    /**
     * Löscht eine einzelne Datei aus einem Ordner.
     */
    @DeleteMapping("/{folderId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @RequestParam String token) {

        fileStorageService.deleteFile(fileId, token);
        return ResponseEntity.noContent().build();
    }
}