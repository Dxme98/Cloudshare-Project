package com.example.demo.EntityTests;

import com.example.demo.entity.FileMetadata;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class FileMetadataTest {

    @Test
    @DisplayName("createFileMetaData - Sollte Objekt korrekt bauen und Upload-Datum setzen")
    void createFileMetaData_shouldCreateInstanceWithTimestamp() {
        // Given
        String fileId = "file-123";
        String fileName = "test.txt";
        String s3Key = "s3-key-xyz";
        Long fileSize = 5000L;
        String folderId = "folder-abc";

        // When
        FileMetadata metadata = FileMetadata.createFileMetaData(fileId, fileName, s3Key, fileSize, folderId);

        // Then
        assertAll("FileMetadata Checks",
                () -> assertThat(metadata.getFileId()).isEqualTo(fileId),
                () -> assertThat(metadata.getFileName()).isEqualTo(fileName),
                () -> assertThat(metadata.getS3Key()).isEqualTo(s3Key),
                () -> assertThat(metadata.getFileSize()).isEqualTo(fileSize),
                () -> assertThat(metadata.getFolderId()).isEqualTo(folderId),
                () -> assertThat(metadata.getUploadDate()).isNotNull(),
                () -> assertThat(metadata.getUploadDate()).isNotEmpty()
        );
    }

    @Test
    @DisplayName("setUploadDate - Sollte aktuellen Zeitstempel setzen")
    void setUploadDate_shouldSetCurrentTime() {
        // Given
        FileMetadata metadata = new FileMetadata();
        assertThat(metadata.getUploadDate()).isNull();

        // When
        metadata.setUploadDate();

        // Then
        assertThat(metadata.getUploadDate()).isNotNull();
    }
}