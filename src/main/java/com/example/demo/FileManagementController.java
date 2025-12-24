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

import java.io.InputStream;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileManagementController {
    private final FileStorageService fileStorageService;

    @PostMapping("/folders")
    public ResponseEntity<FolderResponse> initializeFolder() {
        FolderResponse folderResponse = fileStorageService.initializeFolder();

        return ResponseEntity.status(HttpStatus.CREATED).body(folderResponse);
    }

    @GetMapping("/folders/{folderId}")
    public ResponseEntity<FolderResponse> openFolder(
            @PathVariable String folderId,
            @RequestParam String token) {

        FolderResponse folderResponse = fileStorageService.openFolder(token, folderId);
        return ResponseEntity.ok(folderResponse);
    }

    @PostMapping("/folders/{folderId}")
    public ResponseEntity<String> uploadFile(@PathVariable String folderId, @RequestParam String token, @RequestParam("file") MultipartFile file) {
        String fileId =  fileStorageService.uploadFile(folderId, token, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(fileId);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String fileId, @RequestParam String token) {
        // Resource vom Service holen
        S3Resource resource = fileStorageService.getFileResource(fileId, token);

        // Den ursprünglichen Dateinamen mitschicken
        String filename = resource.getFilename();

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream is = resource.getInputStream()) {
                is.transferTo(outputStream); // Die Daten fließen direkt durch den Container durch
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }
}
