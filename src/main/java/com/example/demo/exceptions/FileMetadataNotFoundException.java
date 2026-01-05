package com.example.demo.exceptions;

public class FileMetadataNotFoundException extends RuntimeException {
    public FileMetadataNotFoundException(String fileId) {
        super("Filemetadata with ID " + fileId + " not found.");;
    }
}
