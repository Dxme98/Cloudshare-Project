package com.example.demo.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.example.demo.entity.FileMetadata;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import java.util.List;

@Getter
@Setter
@Schema(name = "FolderResponse", description = "Detaillierte Ansicht eines Ordners inklusive Inhalt und Metadaten")
public class FolderResponse {

    @Schema(description = "ID des Ordners", example = "f892-kd29-1920")
    private String folderId;

    @Schema(description = "Anzeigename des Ordners", example = "Projekt Alpha Assets")
    private String folderName;

    @Schema(description = "Gibt an, ob der aktuelle Benutzer der Besitzer ist", example = "true")
    @JsonProperty("isOwner")
    private boolean isOwner = false;

    @Schema(description = "Typ des Ordners (z.B. permanent oder temporary)", example = "permanent")
    private String type;

    @Schema(description = "Der Token, der für Share-Links genutzt wird", example = "eyJ...")
    private String shareToken;

    @Schema(description = "Aktuell belegter Speicherplatz in Bytes", example = "10485760") // 10 MB
    private Long usedStorage;

    @Schema(description = "Maximal verfügbarer Speicherplatz in Bytes", example = "536870912") // 512 MB
    private Long maxStorage;

    @Schema(description = "Liste der Dateien in diesem Ordner")
    @JsonProperty("files")
    private List<FileMetadata> fileMetadataList;

    @Schema(description = "Rolle des aktuellen Users in diesem Ordner", example = "OWNER")
    private String role;
}