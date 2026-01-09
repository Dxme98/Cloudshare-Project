package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Schema(name = "FolderSummaryResponse", description = "Zusammenfassung eines Ordners für Listenansichten (Dashboard)")
public class FolderSummaryResponse {

    @Schema(description = "ID des Ordners", example = "uuid-1234")
    private String id;

    @Schema(description = "Name des Ordners", example = "Finanzen 2024")
    private String name;

    @Schema(description = "Erstellungszeitpunkt (ISO-8601 String)", example = "2024-03-20T10:15:30Z")
    private String createdAt;

    @Schema(description = "Anzahl der enthaltenen Dateien", example = "15")
    private long fileCount;

    @Schema(description = "Token für öffentliche Links", example = "eyJ...")
    private String shareToken;
}