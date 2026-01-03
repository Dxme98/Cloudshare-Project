package com.example.demo.controller;

import com.example.demo.model.FolderResponse;
import com.example.demo.service.DashboardService;
import com.example.demo.model.Folder;
import com.example.demo.model.FolderInitResponse;
import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@CrossOrigin(origins = "*")
public class DashboardController {
    private final DashboardService dashboardService;

    /**
     * Listet alle Ordner des eingeloggten Users.
     * Die User-ID kommt sicher aus dem Token (kann nicht gefälscht werden).
     */
    @GetMapping("/folders")
    public ResponseEntity<List<Folder>> getMyFolders(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");

        List<Folder> folders = dashboardService.getMyFolders(userId);
        return ResponseEntity.ok(folders);
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<FolderResponse> openFolder(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");

        FolderResponse response = dashboardService.openFolder(folderId, userId);
        return ResponseEntity.ok(response);
    }

    /**
     * Erstellt einen neuen PERMANENTEN Ordner
     */
    @PostMapping("/folders")
    public ResponseEntity<FolderInitResponse> createPermanentFolder(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(defaultValue = "My Project") String name) {

        String userId = jwt.getClaimAsString("sub");

        FolderInitResponse response = dashboardService.createPermanentFolder(userId, name);
        return ResponseEntity.ok(response);
    }

    /**
     * Upload: Hier brauchen wir KEINEN Token, denn wir haben den JWT User.
     */
    @PostMapping("/{folderId}/files")
    public ResponseEntity<String> uploadFile(
            @PathVariable String folderId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        String fileId = dashboardService.uploadFile(folderId, userId, file);

        return ResponseEntity.status(HttpStatus.CREATED).body(fileId);
    }

    /**
     * Download: User-basiert statt Token-basiert.
     */
    @GetMapping("/{folderId}/files/{fileId}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        S3Resource resource = dashboardService.downloadFile(folderId, fileId, userId);

        String filename = resource.getFilename();
        long contentLength = -1;
        try { contentLength = resource.contentLength(); } catch (Exception ignored) {}

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
     * Delete: Löschen nur wenn es mein Ordner ist.
     */
    @DeleteMapping("/{folderId}/files/{fileId}")
    public ResponseEntity<Void> deleteFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        dashboardService.deleteFile(folderId, fileId, userId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Delete Folder: Ganzen Ordner löschen
     */
    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        dashboardService.deleteFolder(folderId, userId);

        return ResponseEntity.noContent().build();
    }


}
