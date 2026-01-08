package com.example.demo.exceptions.custom;

public class FileMetadataNotFoundException extends RuntimeException {
    public FileMetadataNotFoundException(String fileId) {
        super("Filemetadata with ID " + fileId + " not found.");;
    }
}
