package com.example.demo.exceptions.custom;

public class FileUploadException extends RuntimeException{
    public FileUploadException(String message) {
        super(message);;
    }
}
