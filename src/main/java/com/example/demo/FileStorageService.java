package com.example.demo;

import io.awspring.cloud.s3.S3Template;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.InputStream;

@Service
public class FileStorageService {

    private final S3Template s3Template;
    private final String bucketName;

    public FileStorageService(S3Template s3Template, @Value("${S3_UPLOAD_BUCKET_NAME}") String bucketName ) {
        this.s3Template = s3Template;
        this.bucketName = bucketName;
    }

    public String uploadFile(String originalFilename, InputStream inputStream) {
        String key = "uploads/" + originalFilename;

        s3Template.upload(bucketName, key, inputStream);

        return "Datei erfolgreich hochgeladen als: " + key;
    }
}
