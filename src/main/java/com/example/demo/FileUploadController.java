package com.example.demo;

import io.awspring.cloud.s3.S3Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.InputStream;

@RestController
@RequestMapping("/api/files")
public class FileUploadController {

    private final FileStorageService fileStorageService;

    public FileUploadController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @PostMapping("/upload")
    public ResponseEntity<String> handleFileUpload(@RequestParam("file") MultipartFile file){
        String fileId =  fileStorageService.uploadFile(file);

        return ResponseEntity.status(HttpStatus.CREATED).body(fileId);
    }

    @GetMapping("/{fileId}/download")
    public ResponseEntity<StreamingResponseBody> download(@PathVariable String fileId) {
        // Resource vom Service holen
        S3Resource resource = fileStorageService.getFileResource(fileId);

        // Den ursprünglichen Dateinamen mitschicken
        String filename = resource.getFilename();

        StreamingResponseBody responseBody = outputStream -> {
            try (InputStream is = resource.getInputStream()) {
                is.transferTo(outputStream); // Die Daten fließen direkt durch den Container durch
            }
        };

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(responseBody);
    }
}
