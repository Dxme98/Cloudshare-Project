package com.example.demo.dto.request;

import com.example.demo.enums.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
        name = "ShareRequest",
        description = "Anfrage, um einen Ordner mit einem anderen Benutzer zu teilen"
)
public class ShareRequest {

    @Schema(
            description = "Die E-Mail-Adresse des Benutzers, der Zugriff erhalten soll. Muss eine valide E-Mail sein.",
            example = "kollege@firma.de",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotBlank(message = "Email darf nicht leer sein")
    @Email(message = "Bitte eine gültige E-Mail-Adresse eingeben")
    private String targetEmail;

    @Schema(
            description = "Die Berechtigungsstufe für den Benutzer (z.B. CONTRIBUTOR darf hochladen, VIEWER nur lesen).",
            example = "CONTRIBUTOR",
            requiredMode = Schema.RequiredMode.REQUIRED
    )
    @NotNull(message = "Eine Rolle muss ausgewählt werden")
    private Role role;
}
