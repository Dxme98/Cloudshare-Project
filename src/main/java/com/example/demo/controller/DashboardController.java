package com.example.demo.controller;

import com.example.demo.config.ApiErrorResponses;
import com.example.demo.dto.request.CreateFolderRequest;
import com.example.demo.dto.request.ShareRequest;
import com.example.demo.dto.response.*;
import com.example.demo.service.DashboardService;
import io.awspring.cloud.s3.S3Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Dashboard", description = "Privater Bereich: Verwaltung von eigenen Ordnern und Dateien, Anmeldung nötig.")
@ApiErrorResponses.StandardErrors
@ApiErrorResponses.SecuredEndpoints
@ApiErrorResponses.DatabaseError
public class DashboardController {

    private final DashboardService dashboardService;

    @GetMapping
    @Operation(summary = "Meine Ordner abrufen", description = "Liefert eine Liste aller Ordner, die dem eingeloggten User gehören.")
    @ApiResponse(
            responseCode = "200",
            description = "Liste erfolgreich abgerufen",
            content = @Content(schema = @Schema(implementation = FolderSummaryResponse.class))
    )
    public ResponseEntity<List<FolderSummaryResponse>> getMyFolders(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        List<FolderSummaryResponse> folders = dashboardService.getMyFolders(userId);
        return ResponseEntity.ok(folders);
    }

    @GetMapping("/{folderId}")
    @Operation(summary = "Ordner-Details öffnen", description = "Liefert Metadaten und Inhalt eines spezifischen Ordners.")
    @ApiResponse(
            responseCode = "200",
            description = "Ordner erfolgreich geladen",
            content = @Content(schema = @Schema(implementation = FolderResponse.class))
    )
    @ApiErrorResponses.NotFound
    public ResponseEntity<FolderResponse> openFolder(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        FolderResponse response = dashboardService.openFolder(folderId, userId);
        return ResponseEntity.ok(response);
    }

    @PostMapping
    @Operation(summary = "Neuen Ordner erstellen", description = "Erstellt einen permanenten Ordner für den User.")
    @ApiResponse(
            responseCode = "201",
            description = "Ordner erfolgreich erstellt",
            content = @Content(schema = @Schema(implementation = FolderInitResponse.class))
    )
    public ResponseEntity<FolderInitResponse> createPermanentFolder(
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody CreateFolderRequest createFolderRequest) {
        String userId = jwt.getClaimAsString("sub");
        FolderInitResponse response = dashboardService.createPermanentFolder(userId, createFolderRequest);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{folderId}/members")
    @Operation(summary = "Mitglieder anzeigen", description = "Zeigt an, wer Zugriff auf diesen Ordner hat (Collaborators).")
    @ApiResponse(
            responseCode = "200",
            description = "Mitgliederliste geladen",
            content = @Content(schema = @Schema(implementation = FolderMemberResponse.class))
    )
    @ApiErrorResponses.NotFound
    public ResponseEntity<List<FolderMemberResponse>> getFolderMembers(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        List<FolderMemberResponse> members = dashboardService.getFolderMembers(folderId, userId);
        return ResponseEntity.ok(members);
    }

    @PutMapping("/{folderId}/share-token")
    @Operation(summary = "Share-Token erneuern", description = "Generiert einen neuen öffentlichen Link-Token für den Ordner (alter Token wird ungültig).")
    @ApiResponse(
            responseCode = "200",
            description = "Token erfolgreich erneuert",
            content = @Content(schema = @Schema(implementation = ShareTokenResponse.class))
    )
    public ResponseEntity<ShareTokenResponse> updateShareToken(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        ShareTokenResponse newToken = dashboardService.updateShareToken(folderId, userId);
        return ResponseEntity.ok(newToken);
    }

    @DeleteMapping("/{folderId}/members/{targetUserId}")
    @Operation(summary = "Mitglied entfernen", description = "Entzieht einem User den Zugriff auf den Ordner.")
    @ApiResponse(responseCode = "204", description = "Mitglied erfolgreich entfernt")
    @ApiErrorResponses.NotFound
    public ResponseEntity<Void> removeCollaborator(
            @PathVariable String folderId,
            @PathVariable String targetUserId,
            @AuthenticationPrincipal Jwt jwt) {
        String ownerId = jwt.getClaimAsString("sub");
        dashboardService.removeCollaborator(folderId, ownerId, targetUserId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{folderId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Datei hochladen", description = "Lädt eine Datei in den Ordner hoch (als eingeloggter User).")
    @ApiResponse(
            responseCode = "201",
            description = "Upload erfolgreich",
            content = @Content(schema = @Schema(implementation = FileUploadResponse.class))
    )
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.StorageFull
    public ResponseEntity<FileUploadResponse> uploadFile(
            @PathVariable String folderId,
            @Parameter(description = "Die hochzuladende Datei", required = true)
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        FileUploadResponse response = dashboardService.uploadFile(folderId, userId, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{folderId}/files/{fileId}")
    @Operation(summary = "Datei herunterladen", description = "Streamt die Datei aus S3 durch das Backend zum Client.")
    @ApiResponse(
            responseCode = "200",
            description = "Download gestartet",
            content = @Content(
                    mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    @ApiErrorResponses.NotFound
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) throws IOException {
        String userId = jwt.getClaimAsString("sub");
        S3Resource resource = dashboardService.downloadFile(folderId, fileId, userId);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + resource.getFilename() + "\"")
                .contentLength(resource.contentLength())
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(os -> {
                    try (InputStream is = resource.getInputStream()) {
                        is.transferTo(os);
                    }
                });
    }

    @DeleteMapping("/{folderId}/files/{fileId}")
    @Operation(summary = "Datei löschen", description = "Löscht eine Datei permanent.")
    @ApiResponse(responseCode = "204", description = "Datei erfolgreich gelöscht")
    @ApiErrorResponses.NotFound
    public ResponseEntity<Void> deleteFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        dashboardService.deleteFile(folderId, fileId, userId);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{folderId}")
    @Operation(summary = "Ordner löschen", description = "Löscht den gesamten Ordner inklusive aller Dateien.")
    @ApiResponse(responseCode = "204", description = "Ordner erfolgreich gelöscht")
    @ApiErrorResponses.NotFound
    public ResponseEntity<Void> deleteFolder(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        dashboardService.deleteFolder(folderId, userId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{folderId}/share")
    @Operation(summary = "Ordner teilen", description = "Lädt einen anderen User per E-Mail in den Ordner ein.")
    @ApiResponse(responseCode = "204", description = "Einladung/Freigabe erfolgreich")
    @ApiErrorResponses.NotFound
    public ResponseEntity<Void> shareFolder(
            @PathVariable String folderId,
            @AuthenticationPrincipal Jwt jwt,
            @Valid @RequestBody ShareRequest shareRequest) {
        String userId = jwt.getClaimAsString("sub");
        dashboardService.shareFolder(folderId, userId, shareRequest);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/shared")
    @Operation(summary = "Geteilte Ordner abrufen", description = "Zeigt alle Ordner an, die andere User mit mir geteilt haben.")
    @ApiResponse(
            responseCode = "200",
            description = "Liste erfolgreich geladen",
            content = @Content(schema = @Schema(implementation = SharedFolderResponse.class))
    )
    public ResponseEntity<List<SharedFolderResponse>> getSharedFolders(@AuthenticationPrincipal Jwt jwt) {
        String userId = jwt.getClaimAsString("sub");
        List<SharedFolderResponse> sharedFolders = dashboardService.getSharedFolders(userId);
        return ResponseEntity.ok(sharedFolders);
    }
}