package com.example.demo.dto.response;

import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class FileUploadResponse {
    String fileId;

    public static FileUploadResponse create(String fileId) {
        FileUploadResponse response = new FileUploadResponse();
        response.setFileId(fileId);

        return response;
    }
}
