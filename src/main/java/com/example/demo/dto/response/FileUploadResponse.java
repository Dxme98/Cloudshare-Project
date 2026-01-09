package com.example.demo.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
@Schema(name = "FileUploadResponse", description = "Bestätigung eines erfolgreichen Datei-Uploads")
public class FileUploadResponse {

    @Schema(description = "Die eindeutige ID der hochgeladenen Datei (wird für Download/Löschen benötigt)", example = "a1b2c3d4-e5f6-7890-1234-56789abcdef0")
    String fileId;

    public static FileUploadResponse create(String fileId) {
        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(fileId);
        return response;
    }
}
