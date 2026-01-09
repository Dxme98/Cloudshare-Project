package com.example.demo.dto.response;


import com.example.demo.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(name = "SharedFolderResponse", description = "Informationen über einen Ordner, der mit dem User geteilt wurde")
public class SharedFolderResponse {

    @Schema(description = "ID des Users, dem der Ordner freigegeben wurde", example = "user-123")
    private String userId;

    @Schema(description = "ID des geteilten Ordners", example = "folder-xyz")
    private String folderId;

    @Schema(description = "Die Rolle, die dem User zugewiesen wurde", example = "VIEWER")
    private Role role;

    @Schema(description = "ID des Besitzers (Owner)", example = "owner-999")
    private String ownerId;

    @Schema(description = "Name des geteilten Ordners", example = "Gemeinsames Projekt")
    private String folderName;
}