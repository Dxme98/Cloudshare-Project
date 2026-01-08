package com.example.demo.exceptions.custom;

public class StorageLimitExceededException extends RuntimeException {

    public StorageLimitExceededException(long currentSizeBytes, long limitBytes) {
        super(String.format("Folder storage limit reached. Limit: %d MB, Current: %d MB",
                limitBytes / (1024 * 1024),
                currentSizeBytes / (1024 * 1024)));
    }

    public StorageLimitExceededException(String message) {
        super(message);
    }
}
