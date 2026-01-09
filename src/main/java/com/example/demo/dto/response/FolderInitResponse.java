package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "FolderInitResponse", description = "Antwort nach der Initialisierung eines (meist temporären) Ordners")
public class FolderInitResponse {

    @Schema(description = "Die eindeutige ID des erstellten Ordners", example = "folder-123-uuid")
    private String folderId;

    @Schema(
            description = "Der Admin-Token für den Ersteller. Ermöglicht Löschen und Verwalten des Ordners. Dieser ist NULL bei angemeldeten Usern! (da die Authentifizierung über den Account läuft).",
            example = "eyJhbGciOiJIUzI1NiIsIn...",
            nullable = true
    )
    private String ownerToken;

    @Schema(
            description = "Der öffentliche Token zum Teilen (nur Lesezugriff/Upload, je nach Konfig).",
            example = "eyJhbGciOiJIUzI1NiIsIn..."
    )
    private String shareToken;
}