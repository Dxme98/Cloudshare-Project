package com.example.demo.controller;

import com.example.demo.config.ApiErrorResponses;
import com.example.demo.dto.response.FileUploadResponse;
import com.example.demo.dto.response.FolderInitResponse;
import com.example.demo.dto.response.FolderResponse;
import com.example.demo.service.PublicShareService;
import io.awspring.cloud.s3.S3Resource;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "Öffentliche Ordner", description = "Endpunkte für anonyme Ordnerverwaltung (Upload/Download ohne Account)")
@ApiErrorResponses.StandardErrors
@ApiErrorResponses.DatabaseError
public class PublicFolderController {

    private final PublicShareService publicShareService;

    @PostMapping
    @Operation(
            summary = "Neuen temporären 24h-Ordner erstellen",
            description = "Erstellt einen temporären Ordner ohne Benutzerauthentifizierung. Gibt die Zugriffstoken zurück. Der Ordner existiert für 24 Stunden."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Ordner erfolgreich erstellt",
            content = @Content(schema = @Schema(implementation = FolderInitResponse.class))
    )
    public ResponseEntity<FolderInitResponse> initializeFolder() {
        FolderInitResponse folderResponse = publicShareService.initializeFolder();
        return ResponseEntity.status(HttpStatus.CREATED).body(folderResponse);
    }

    @GetMapping("/{folderId}")
    @Operation(
            summary = "Ordner via Token öffnen",
            description = "Ruft Ordner-Metadaten und die Dateiliste ab. Erfordert einen gültigen Share-Token."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Ordner erfolgreich abgerufen",
            content = @Content(schema = @Schema(implementation = FolderResponse.class))
    )
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.InvalidToken
    public ResponseEntity<FolderResponse> openFolder(
            @PathVariable String folderId,
            @Parameter(description = "Der Share-Token, der bei der Erstellung empfangen wurde", required = true)
            @RequestParam String token) {

        FolderResponse folderResponse = publicShareService.openFolder(token, folderId);
        return ResponseEntity.ok(folderResponse);
    }

    @PostMapping(value = "/{folderId}/files", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Datei hochladen",
            description = "Lädt eine Datei in den angegebenen Ordner hoch. Erfordert einen Token mit Schreibrechten (Contributor oder Owner)."
    )
    @ApiResponse(
            responseCode = "201",
            description = "Datei erfolgreich hochgeladen",
            content = @Content(schema = @Schema(implementation = FileUploadResponse.class))
    )
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.InvalidToken
    @ApiErrorResponses.StorageFull
    public ResponseEntity<FileUploadResponse> uploadFile(
            @PathVariable String folderId,
            @Parameter(description = "Der Share-Token mit Schreibrechten", required = true)
            @RequestParam String token,
            @Parameter(description = "Die hochzuladende Datei", required = true)
            @RequestParam("file") MultipartFile file) {

        FileUploadResponse response = publicShareService.uploadFileWithToken(folderId, token, file);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{folderId}/files/{fileId}")
    @Operation(
            summary = "Datei herunterladen",
            description = "Streamt den Dateiinhalt von S3 durch das Backend zum Client."
    )
    @ApiResponse(
            responseCode = "200",
            description = "Download-Stream gestartet",
            content = @Content(
                    mediaType = MediaType.APPLICATION_OCTET_STREAM_VALUE,
                    schema = @Schema(type = "string", format = "binary")
            )
    )
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.InvalidToken
    public ResponseEntity<StreamingResponseBody> downloadFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @Parameter(description = "Der Token mit Leserechten", required = true)
            @RequestParam String token) throws IOException {

        S3Resource resource = publicShareService.downloadFile(folderId, fileId, token);

        String filename = resource.getFilename();
        long contentLength = resource.contentLength();

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

    @DeleteMapping("/{folderId}/files/{fileId}")
    @Operation(
            summary = "Datei löschen",
            description = "Löscht eine Datei permanent aus dem Ordner. Erfordert den OWNER-Token."
    )
    @ApiResponse(
            responseCode = "204",
            description = "Datei erfolgreich gelöscht"
    )
    @ApiErrorResponses.NotFound
    @ApiErrorResponses.InvalidToken
    public ResponseEntity<Void> deleteFile(
            @PathVariable String folderId,
            @PathVariable String fileId,
            @Parameter(description = "Der Share-Token mit OWNER-Rechten", required = true)
            @RequestParam String token) {

        publicShareService.deleteFileWithToken(folderId, fileId, token);
        return ResponseEntity.noContent().build();
    }
}