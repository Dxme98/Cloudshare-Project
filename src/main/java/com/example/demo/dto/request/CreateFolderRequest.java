package com.example.demo.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Schema(
        name = "CreateFolderRequest",
        description = "Payload für die Erstellung eines neuen Ordners (permanent oder temporär)"
)
public class CreateFolderRequest {

    @Schema(
            description = "Der Name des neuen Ordners. Darf nicht leer sein und maximal 50 Zeichen haben.",
            example = "Urlaubsfotos 2024",
            minLength = 1,
            maxLength = 50
    )
    @NotBlank(message = "Folder name must not be empty")
    @Size(min = 1, max = 50, message = "Name too long")
    String name;
}
