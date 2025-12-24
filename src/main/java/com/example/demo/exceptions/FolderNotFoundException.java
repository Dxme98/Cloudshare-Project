package com.example.demo.exceptions;

public class FolderNotFoundException extends RuntimeException {
    public FolderNotFoundException(String id) {
        super("Folder with ID " + id + " not found.");
    }
}
