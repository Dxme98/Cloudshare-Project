package com.example.demo.ServiceIntegrationTests;

import com.example.demo.TestHelper.TestDataFactory;
import com.example.demo.entity.FileMetadata;
import com.example.demo.exceptions.custom.FileUploadException;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.service.FileStorageService;
import io.awspring.cloud.s3.S3Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseBytes;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class FileStorageServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private FileMetadataRepository fileMetadataRepository;

    @Autowired
    private TestDataFactory testDataFactory;

    @Autowired
    private S3Client s3Client;

    private static final String BUCKET_NAME = "test-bucket-name";
    private static final String FOLDER_ID = "folder-" + UUID.randomUUID();

    // ==================== UPLOAD TESTS ====================

    @Test
    @DisplayName("Should upload file to S3 and save metadata")
    void shouldUploadFileToS3AndSaveMetadata() {
        // Given
        String content = "Hello S3!";
        MultipartFile file = createMockFile("upload-test.txt", content);

        // When
        FileMetadata metadata = fileStorageService.uploadFile(FOLDER_ID, file);

        // Then Metadata
        assertThat(metadata.getFileId()).isNotNull();
        assertThat(metadata.getFolderId()).isEqualTo(FOLDER_ID);
        assertThat(metadata.getS3Key()).contains("uploads/" + FOLDER_ID);

        FileMetadata savedMeta = fileMetadataRepository.findById(metadata.getFileId());
        assertThat(savedMeta).isNotNull();

        // Then S3
        ResponseBytes<GetObjectResponse> s3Object = s3Client.getObjectAsBytes(b -> b
                .bucket(BUCKET_NAME)
                .key(metadata.getS3Key()));

        String s3Content = s3Object.asUtf8String();
        assertThat(s3Content).isEqualTo(content);
    }

    @Test
    @DisplayName("Should throw exception if stream fails")
    void shouldThrowExceptionIfStreamFails() throws IOException {
        // Given
        MultipartFile badFile = mock(MultipartFile.class);
        when(badFile.getOriginalFilename()).thenReturn("crash.txt");
        when(badFile.getInputStream()).thenThrow(new IOException("Disk Error"));

        // When & Then
        assertThatThrownBy(() -> fileStorageService.uploadFile(FOLDER_ID, badFile))
                .isInstanceOf(FileUploadException.class);
    }

    // ==================== DOWNLOAD TESTS ====================

    @Test
    @DisplayName("Should download existing file from S3")
    void shouldDownloadFileFromS3() throws IOException {
        // Given
        String content = "Download Content";
        FileMetadata metadata = testDataFactory.createFile(FOLDER_ID, "download.txt", 100L);

        s3Client.putObject(b -> b.bucket(BUCKET_NAME).key(metadata.getS3Key()),
                RequestBody.fromString(content));

        // When
        S3Resource resource = fileStorageService.downloadFile(metadata.getFileId());

        // Then
        assertThat(resource).isNotNull();
        String downloadedContent = new String(resource.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(downloadedContent).isEqualTo(content);
    }

    @Test
    @DisplayName("Should fail download if metadata missing")
    void shouldFailDownloadIfMetadataMissing() {
        assertThatThrownBy(() -> fileStorageService.downloadFile("non-existent-id"))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== DELETE TESTS ====================

    @Test
    @DisplayName("Should delete file from S3 and Database")
    void shouldDeleteFileFromS3AndDatabase() {
        // Given
        FileMetadata metadata = testDataFactory.createFile(FOLDER_ID, "delete-me.txt", 50L);
        s3Client.putObject(b -> b.bucket(BUCKET_NAME).key(metadata.getS3Key()),
                RequestBody.fromString("Trash"));

        // When
        fileStorageService.deleteFile(metadata.getFileId());

        // Then: DB check
        assertThatThrownBy(() -> fileMetadataRepository.findById(metadata.getFileId()))
                .isInstanceOf(RuntimeException.class);

        // Then: S3 check
        assertThatThrownBy(() -> s3Client.headObject(b -> b.bucket(BUCKET_NAME).key(metadata.getS3Key())))
                .isInstanceOf(NoSuchKeyException.class);
    }

    @Test
    @DisplayName("Should handle delete gracefully if S3 object already gone")
    void shouldHandleDeleteIfS3Gone() {
        FileMetadata metadata = testDataFactory.createFile(FOLDER_ID, "already-gone.txt", 50L);

        // When
        fileStorageService.deleteFile(metadata.getFileId());

        // Then
        assertThatThrownBy(() -> fileMetadataRepository.findById(metadata.getFileId()))
                .isInstanceOf(RuntimeException.class);
    }

    // ==================== LIST TESTS ====================

    @Test
    @DisplayName("Should list files only for specific folder")
    void shouldListFilesInFolder() {
        // Given
        String uniqueTargetFolderId = "folder-" + UUID.randomUUID();
        String noiseFolderId = "folder-" + UUID.randomUUID();

        testDataFactory.createFile(uniqueTargetFolderId, "target-1.txt", 10L);
        testDataFactory.createFile(uniqueTargetFolderId, "target-2.txt", 20L);
        testDataFactory.createFile(noiseFolderId, "other.txt", 30L);

        // When
        List<FileMetadata> files = fileStorageService.getFilesInFolder(uniqueTargetFolderId);

        // Then
        assertThat(files).hasSize(2);
        assertThat(files)
                .extracting(FileMetadata::getFileName)
                .containsExactlyInAnyOrder("target-1.txt", "target-2.txt");
    }

    // ==================== HELPER ====================

    private MultipartFile createMockFile(String filename, String content) {
        MultipartFile file = mock(MultipartFile.class);
        when(file.getOriginalFilename()).thenReturn(filename);
        when(file.getSize()).thenReturn((long) content.length());
        try {
            when(file.getInputStream()).thenReturn(new ByteArrayInputStream(content.getBytes()));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }
}