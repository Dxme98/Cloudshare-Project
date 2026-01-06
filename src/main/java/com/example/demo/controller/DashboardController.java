package com.example.demo.controller;

import com.example.demo.dto.request.CreateFolderRequest;
import com.example.demo.dto.request.ShareRequest;
import com.example.demo.dto.response.*;
import com.example.demo.service.DashboardService;
import io.awspring.cloud.s3.S3Resource;
import jakarta.validation.Valid;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

@RestController
@RequestMapping("/api/dashboard/folders")
@RequiredArgsConstructor
public class DashboardController {
    private final DashboardService dashboardService;

    /**
     * Listet alle Ordner des eingeloggten Users.
     * Die User-ID kommt sicher aus dem Token (kann nicht gefälscht werden).
     */
    @GetMapping
    public ResponseEntity<List<FolderSummaryResponse>> getMyFolders(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");

        List<FolderSummaryResponse> folders = dashboardService.getMyFolders(userId);
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
     * Erstellt einen neuen PERMANENTEN Ordner.
     */
    @PostMapping
    public ResponseEntity<FolderInitResponse> createPermanentFolder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateFolderRequest createFolderRequest) {

        String userId = jwt.getClaimAsString("sub");

        FolderInitResponse response = dashboardService.createPermanentFolder(userId, createFolderRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{folderId}/members")
    public ResponseEntity<List<FolderMemberResponse>> getFolderMembers(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        List<FolderMemberResponse> members = dashboardService.getFolderMembers(folderId, userId);

        return ResponseEntity.ok(members);
    }

    @PutMapping("/{folderId}/share-token")
    public ResponseEntity<ShareTokenResponse> updateShareToken(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        ShareTokenResponse newToken = dashboardService.updateShareToken(folderId, userId);

        return ResponseEntity.ok(newToken);
    }

    @DeleteMapping("/{folderId}/members/{targetUserId}")
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable String folderId,
            @PathVariable String targetUserId,
            @AuthenticationPrincipal Jwt jwt) {

        String ownerId = jwt.getClaimAsString("sub");

        dashboardService.removeCollaborator(folderId, ownerId, targetUserId);

        return ResponseEntity.noContent().build();
    }

    /**
     * Upload: Hier brauchen wir KEINEN Token, denn wir haben den JWT User.
     */
    @PostMapping("/{folderId}/files")
    public ResponseEntity<FileUploadResponse> uploadFile(
            @PathVariable String folderId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {

        String userId = jwt.getClaimAsString("sub");
        FileUploadResponse response = dashboardService.uploadFile(folderId, userId, file);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Download: User-basiert statt Token-basiert.
     */
    @GetMapping("/{folderId}/files/{fileId}")
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) throws IOException {

        String userId = jwt.getClaimAsString("sub");
        S3Resource resource = dashboardService.downloadFile(folderId, fileId, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentLength(resource.contentLength()) // Kann IOException werfen
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(os -> {
                    try (InputStream is = resource.getInputStream()) {
                        is.transferTo(os);
                    }
                });
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

    @PostMapping("/{folderId}/share")
    public ResponseEntity<Void> shareFolder(@PathVariable String folderId, @AuthenticationPrincipal Jwt jwt,
                                            @Valid @RequestBody ShareRequest shareRequest) {
        String userId = jwt.getClaimAsString("sub");
        dashboardService.shareFolder(folderId, userId, shareRequest);

        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared")
    public ResponseEntity<List<SharedFolderResponse>> getSharedFolders(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");

        List<SharedFolderResponse> sharedFolders = dashboardService.getSharedFolders(userId);

        return ResponseEntity.ok(sharedFolders);
    }
}
