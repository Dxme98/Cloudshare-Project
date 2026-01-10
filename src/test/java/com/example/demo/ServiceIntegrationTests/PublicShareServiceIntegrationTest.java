package com.example.demo.ServiceIntegrationTests;

import com.example.demo.TestHelper.TestDataFactory;
import com.example.demo.dto.response.FileUploadResponse;
import com.example.demo.dto.response.FolderInitResponse;
import com.example.demo.dto.response.FolderResponse;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.Folder;
import com.example.demo.enums.FolderType;
import com.example.demo.enums.Role;
import com.example.demo.exceptions.custom.FileMetadataNotFoundException;
import com.example.demo.exceptions.custom.InvalidTokenException;
import com.example.demo.exceptions.custom.StorageLimitExceededException;
import com.example.demo.service.PublicShareService;
import io.awspring.cloud.s3.S3Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicShareServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private PublicShareService publicShareService;

    @Autowired
    TestDataFactory testDataFactory;

    @Autowired
    private S3Client s3Client;

    private static final String BUCKET_NAME = "test-bucket-name";

    // ==================== INITIALIZATION & ACCESS ====================

    @Test
    @DisplayName("Should initialize temporary folder with tokens")
    void shouldInitializeTemporaryFolder() {
        // When
        FolderInitResponse response = publicShareService.initializeFolder();

        // Then
        assertThat(response.getFolderId()).isNotNull();
        assertThat(response.getOwnerToken()).isNotNull();
        assertThat(response.getShareToken()).isNotNull();

        Folder saved = folderRepository.findById(response.getFolderId());
        assertThat(saved.getFolderName()).isEqualTo("Temporary Folder");
        assertThat(saved.getType()).isEqualTo(FolderType.TEMPORARY);
        assertThat(saved.getOwnerToken()).isEqualTo(response.getOwnerToken());
    }

    @Test
    @DisplayName("Should open folder as OWNER using owner token")
    void shouldOpenFolderAsOwner() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();
        testDataFactory.createFile(init.getFolderId(), "test.txt", 100L);

        // When
        FolderResponse response = publicShareService.openFolder(init.getOwnerToken(), init.getFolderId());

        // Then
        assertThat(response.getRole()).isEqualTo(Role.OWNER.name());
        assertThat(response.getFileMetadataList()).hasSize(1);
    }

    @Test
    @DisplayName("Should open folder as VIEWER using share token")
    void shouldOpenFolderAsViewer() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();

        // When
        FolderResponse response = publicShareService.openFolder(init.getShareToken(), init.getFolderId());

        // Then
        assertThat(response.getRole()).isEqualTo(Role.VIEWER.name());
    }

    @Test
    @DisplayName("Should throw exception for invalid token")
    void shouldThrowOnInvalidToken() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();

        // When & Then
        assertThatThrownBy(() ->
                publicShareService.openFolder("invalid-token-123", init.getFolderId()))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Should handle null owner token, for permanent folders")
    void shouldThrowOnNullToken() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();

        // When & Then
        assertThatThrownBy(() ->
                publicShareService.openFolder(null, init.getFolderId()))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ==================== UPLOAD OPERATIONS ====================

    @Test
    @DisplayName("Should upload file with OWNER token")
    void shouldUploadFileWithOwnerToken() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();
        MultipartFile file = createMockFile("upload.pdf", 2048L);

        // When
        FileUploadResponse response = publicShareService.uploadFileWithToken(
                init.getFolderId(), init.getOwnerToken(), file);

        // Then
        assertThat(response.getFileId()).isNotNull();

        // Check DB
        FileMetadata metadata = fileMetadataRepository.findById(response.getFileId());
        assertThat(metadata.getFileName()).isEqualTo("upload.pdf");

        // Check Folder Stats
        Folder folder = folderRepository.findById(init.getFolderId());
        assertThat(folder.getUsedStorage()).isEqualTo(2048L);
    }

    @Test
    @DisplayName("Should reject upload with SHARE token (Viewer)")
    void shouldRejectUploadWithShareToken() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();
        MultipartFile file = createMockFile("hacker.exe", 100L);

        // When & Then
        assertThatThrownBy(() ->
                publicShareService.uploadFileWithToken(init.getFolderId(), init.getShareToken(), file))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Should reject upload if storage limit exceeded")
    void shouldRejectUploadIfQuotaExceeded() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();

        long limit = 500 * 1024 * 1024;
        FileMetadata largeFile = testDataFactory.createFile(init.getFolderId(), "existing.big", limit);
        folderRepository.incrementFolderStats(init.getFolderId(), limit);

        MultipartFile newFile = createMockFile("straw-camel.pdf", 1024L);

        // When & Then
        assertThatThrownBy(() ->
                publicShareService.uploadFileWithToken(init.getFolderId(), init.getOwnerToken(), newFile))
                .isInstanceOf(StorageLimitExceededException.class);
    }

    // ==================== DOWNLOAD OPERATIONS ====================

    @Test
    @DisplayName("Should download file with SHARE token")
    void shouldDownloadFileWithShareToken() throws IOException {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();

        FileMetadata metadata = testDataFactory.createFile(init.getFolderId(), "public-doc.txt", 50L);
        s3Client.putObject(b -> b.bucket(BUCKET_NAME).key(metadata.getS3Key()),
                RequestBody.fromString("Public Content"));

        // When
        S3Resource resource = publicShareService.downloadFile(init.getFolderId(), metadata.getFileId(), init.getShareToken());

        // Then
        assertThat(resource).isNotNull();
        String content = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(content).isEqualTo("Public Content");
    }

    @Test
    @DisplayName("Should prevent downloading file from another folder")
    void shouldPreventCrossFolderAccess() {
        // Given
        FolderInitResponse folderA = publicShareService.initializeFolder();
        FolderInitResponse folderB = publicShareService.initializeFolder(); // Hacker Folder

        FileMetadata secretFile = testDataFactory.createFile(folderA.getFolderId(), "secret.txt", 10L);

        assertThatThrownBy(() ->
                publicShareService.downloadFile(folderB.getFolderId(), secretFile.getFileId(), folderB.getOwnerToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ==================== DELETE OPERATIONS ====================

    @Test
    @DisplayName("Should delete file with OWNER token")
    void shouldDeleteFileWithOwnerToken() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();
        FileMetadata file = testDataFactory.createFile(init.getFolderId(), "trash.tmp", 500L);
        folderRepository.incrementFolderStats(init.getFolderId(), 500L);

        s3Client.putObject(b -> b.bucket(BUCKET_NAME).key(file.getS3Key()), RequestBody.fromString("x"));

        // When
        publicShareService.deleteFileWithToken(init.getFolderId(), file.getFileId(), init.getOwnerToken());

        // Then
        assertThatThrownBy(() ->
                fileMetadataRepository.findById(file.getFileId()))
                .isInstanceOf(FileMetadataNotFoundException.class);

        Folder updated = folderRepository.findById(init.getFolderId());
        assertThat(updated.getUsedStorage()).isEqualTo(0L);
    }

    @Test
    @DisplayName("Should reject delete with SHARE token")
    void shouldRejectDeleteWithShareToken() {
        // Given
        FolderInitResponse init = publicShareService.initializeFolder();
        FileMetadata file = testDataFactory.createFile(init.getFolderId(), "precious.img", 100L);

        // When & Then
        assertThatThrownBy(() ->
                publicShareService.deleteFileWithToken(init.getFolderId(), file.getFileId(), init.getShareToken()))
                .isInstanceOf(InvalidTokenException.class);
    }

    // ==================== HELPER ====================

    private MultipartFile createMockFile(String filename, long size) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getSize()).thenReturn(size);
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream("Test Content".getBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }
}