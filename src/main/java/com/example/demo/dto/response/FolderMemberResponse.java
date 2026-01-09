package com.example.demo.dto.response;

import com.example.demo.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@Schema(name = "FolderMemberResponse", description = "Informationen über ein Mitglied, das Zugriff auf einen Ordner hat")
public class FolderMemberResponse {

    @Schema(description = "Die interne User-ID (Cognito Sub)", example = "us-east-1:12345678-abcd-...")
    private String userId;

    @Schema(description = "Die E-Mail-Adresse des Benutzers", example = "mitarbeiter@firma.de")
    private String email;

    @Schema(description = "Die zugewiesene Rolle im Ordner", example = "CONTRIBUTOR")
    private Role role;
}