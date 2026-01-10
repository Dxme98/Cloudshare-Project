package com.example.demo.ServiceIntegrationTests;

import com.example.demo.TestHelper.TestDataFactory;
import com.example.demo.dto.request.CreateFolderRequest;
import com.example.demo.dto.request.ShareRequest;
import com.example.demo.dto.response.*;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.Folder;
import com.example.demo.entity.FolderShare;
import com.example.demo.enums.FolderType;
import com.example.demo.enums.Role;
import com.example.demo.exceptions.custom.FolderNotFoundException;
import com.example.demo.exceptions.custom.InvalidTokenException;
import com.example.demo.exceptions.custom.StorageLimitExceededException;
import com.example.demo.exceptions.custom.UserAlreadyHasAccessException;
import com.example.demo.service.DashboardService;
import com.example.demo.service.FileStorageService;
import com.example.demo.service.UserLookupService;
import io.awspring.cloud.s3.S3Resource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class DashboardServiceIntegrationTest extends AbstractIntegrationTest {
    @Autowired
    TestDataFactory testDataFactory;

    @Autowired
    private DashboardService dashboardService;

    @Autowired
    private FileStorageService fileStorageService;

    @Autowired
    private S3Client s3Client;

    @MockitoBean
    private UserLookupService userLookupService;

    private static final String USER_ID = "user-123";
    private static final String OTHER_USER_ID = "user-456";
    private static final String BUCKET_NAME = "test-bucket-name";

    // ==================== FOLDER OPERATIONS ====================

    @Test
    @DisplayName("Should create permanent folder with correct attributes")
    void shouldCreatePermanentFolder() {
        // Given
        CreateFolderRequest request = new CreateFolderRequest();
        request.setName("My Documents");

        // When
        FolderInitResponse response = dashboardService.createPermanentFolder(USER_ID, request);

        // Then
        assertThat(response.getFolderId()).isNotNull();
        assertThat(response.getShareToken()).isNotNull();
        assertThat(response.getOwnerToken()).isNull();

        Folder saved = folderRepository.findById(response.getFolderId());
        assertThat(saved.getFolderName()).isEqualTo("My Documents");
        assertThat(saved.getUserId()).isEqualTo(USER_ID);
        assertThat(saved.getType()).isEqualTo(FolderType.PERMANENT);
    }

    @Test
    @DisplayName("Should get all folders for a user")
    void shouldGetMyFolders() {
        String uniqueUserId = "user-" + java.util.UUID.randomUUID();

        testDataFactory.createFolder(uniqueUserId, "Folder 1");
        testDataFactory.createFolder(uniqueUserId, "Folder 2");
        testDataFactory.createFolder(OTHER_USER_ID, "Other Folder");

        List<FolderSummaryResponse> folders = dashboardService.getMyFolders(uniqueUserId);

        assertThat(folders).hasSize(2);
        assertThat(folders)
                .extracting(FolderSummaryResponse::getName)
                .containsExactlyInAnyOrder("Folder 1", "Folder 2");
    }

    @Test
    @DisplayName("Should open folder as owner with full details")
    void shouldOpenFolderAsOwner() {
        // Given
        Folder folder = testDataFactory.createFolder(USER_ID, "Test Folder");

        testDataFactory.createFile(folder.getFolderId(), "doc1.pdf", 1024L);
        testDataFactory.createFile(folder.getFolderId(), "doc2.pdf", 2048L);

        // When
        FolderResponse response = dashboardService.openFolder(folder.getFolderId(), USER_ID);

        // Then
        assertThat(response.getFolderName()).isEqualTo("Test Folder");
        assertThat(response.getRole()).isEqualTo(Role.OWNER.name());
        assertThat(response.getFileMetadataList()).hasSize(2);
    }

    @Test
    @DisplayName("Should open shared folder with correct role")
    void shouldOpenSharedFolderWithViewerRole() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Shared Folder");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.VIEWER, USER_ID, "Shared Folder");

        FolderResponse response = dashboardService.openFolder(folder.getFolderId(), OTHER_USER_ID);

        assertThat(response.getRole()).isEqualTo(Role.VIEWER.name());
    }

    @Test
    @DisplayName("Should throw AccessDeniedException when user has no access")
    void shouldDenyAccessToUnauthorizedUser() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Private Folder");

        assertThatThrownBy(() -> dashboardService.openFolder(folder.getFolderId(), OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should delete folder as owner")
    void shouldDeleteFolder() {
        Folder folder = testDataFactory.createFolder(USER_ID, "To Delete");

        dashboardService.deleteFolder(folder.getFolderId(), USER_ID);

        assertThatThrownBy(() -> folderRepository.findById(folder.getFolderId()))
                .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    @DisplayName("Should not allow non-owner to delete folder")
    void shouldNotAllowNonOwnerToDeleteFolder() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Protected Folder");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Protected");

        assertThatThrownBy(() -> dashboardService.deleteFolder(folder.getFolderId(), OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== FILE OPERATIONS ====================

    @Test
    @DisplayName("Should upload file as contributor")
    void shouldUploadFileAsContributor() {
        // Given
        Folder folder = testDataFactory.createFolder(USER_ID, "Upload Folder");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Upload Folder");

        MultipartFile file = createMockFile("test.pdf", 5000L);

        // When
        FileUploadResponse response = dashboardService.uploadFile(folder.getFolderId(), OTHER_USER_ID, file);

        // Then
        assertThat(response).isNotNull();

        // 1. DB Prüfung
        FileMetadata saved = fileMetadataRepository.findById(response.getFileId());
        assertThat(saved.getFolderId()).isEqualTo(folder.getFolderId());
        assertThat(saved.getFileName()).isEqualTo("test.pdf");

        // 2. Folder Stats Prüfung
        Folder updatedFolder = folderRepository.findById(folder.getFolderId());
        assertThat(updatedFolder.getUsedStorage()).isEqualTo(5000L);
    }

    @Test
    @DisplayName("Should reject upload when storage limit exceeded")
    void shouldRejectUploadWhenStorageLimitExceeded() {
        // Given
        Folder folder = testDataFactory.createFolder(USER_ID, "Full Folder");
        MultipartFile file = createMockFile("large.pdf", 100 * 1024 * 1024L);

        FileMetadata hugeFile = testDataFactory.createFile(folder.getFolderId(), "blocker.bin", 1024L * 1024 * 1024); // 1GB
        folderRepository.incrementFolderStats(folder.getFolderId(), hugeFile.getFileSize());

        // When & Then
        assertThatThrownBy(() ->
                dashboardService.uploadFile(folder.getFolderId(), USER_ID, file))
                .isInstanceOf(StorageLimitExceededException.class);

        assertThat(fileMetadataRepository.findAllByFolderId(folder.getFolderId())).hasSize(1);
    }

    @Test
    @DisplayName("Should not allow viewer to upload files")
    void shouldNotAllowViewerToUploadFiles() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Read-Only Folder");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.VIEWER, USER_ID, "Read-Only");
        MultipartFile file = createMockFile("test.pdf", 1000L);

        assertThatThrownBy(() ->
                dashboardService.uploadFile(folder.getFolderId(), OTHER_USER_ID, file))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should download file as viewer")
    void shouldDownloadFileAsViewer() {
        // Given
        Folder folder = testDataFactory.createFolder(USER_ID, "Downloads");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.VIEWER, USER_ID, "Downloads");

        FileMetadata metadata = testDataFactory.createFile(folder.getFolderId(), "real-content.txt", 12L);

        s3Client.putObject(b -> b.bucket(BUCKET_NAME).key(metadata.getS3Key()),
                RequestBody.fromString("Hello World!"));

        // When
        S3Resource result = dashboardService.downloadFile(
                folder.getFolderId(), metadata.getFileId(), OTHER_USER_ID);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getFilename()).isEqualTo("test/real-content.txt");

        try {
            String content = new String(result.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertThat(content).isEqualTo("Hello World!");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    @DisplayName("Should reject download if file is in different folder")
    void shouldRejectDownloadIfFileInDifferentFolder() {
        // Given
        Folder folder1 = testDataFactory.createFolder(USER_ID, "Folder 1");
        Folder folder2 = testDataFactory.createFolder(USER_ID, "Folder 2");
        FileMetadata file = testDataFactory.createFile(folder2.getFolderId(), "secret.pdf", 1024L);

        // When & Then
        assertThatThrownBy(() ->
                dashboardService.downloadFile(folder1.getFolderId(), file.getFileId(), USER_ID))
                .isInstanceOf(InvalidTokenException.class);
    }

    @Test
    @DisplayName("Should delete file as contributor")
    void shouldDeleteFileAsContributor() {
        // Given
        Folder folder = testDataFactory.createFolder(USER_ID, "Delete Test");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Delete Test");

        FileMetadata file = testDataFactory.createFile(folder.getFolderId(), "to-delete.pdf", 3000L);
        s3Client.putObject(b -> b.bucket(BUCKET_NAME).key(file.getS3Key()),
                RequestBody.fromString("Delete Me"));

        // When
        dashboardService.deleteFile(folder.getFolderId(), file.getFileId(), OTHER_USER_ID);

        // Then
        assertThatThrownBy(() -> fileMetadataRepository.findById(file.getFileId()))
                .isInstanceOf(RuntimeException.class);
    }

    @Test
    @DisplayName("Should not allow viewer to delete files")
    void shouldNotAllowViewerToDeleteFiles() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Protected");
        FileMetadata file = testDataFactory.createFile(folder.getFolderId(), "protected.pdf", 1000L);
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.VIEWER, USER_ID, "Protected");

        assertThatThrownBy(() ->
                dashboardService.deleteFile(folder.getFolderId(), file.getFileId(), OTHER_USER_ID))
                .isInstanceOf(AccessDeniedException.class);
    }

    // ==================== SHARING ====================

    @Test
    @DisplayName("Should share folder with another user")
    void shouldShareFolder() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Shared Project");
        String targetEmail = "colleague@example.com";

        when(userLookupService.findUserIdByEmail(targetEmail)).thenReturn(OTHER_USER_ID);

        ShareRequest request = new ShareRequest();
        request.setTargetEmail(targetEmail);
        request.setRole(Role.CONTRIBUTOR);

        dashboardService.shareFolder(folder.getFolderId(), USER_ID, request);

        verify(userLookupService).findUserIdByEmail(targetEmail);
        FolderShare share = shareRepository.findAccess(OTHER_USER_ID, folder.getFolderId()).orElseThrow();
        assertThat(share.getRole()).isEqualTo(Role.CONTRIBUTOR);
    }

    @Test
    @DisplayName("Should not allow sharing with self")
    void shouldNotAllowSharingWithSelf() {
        Folder folder = testDataFactory.createFolder(USER_ID, "My Folder");
        String myEmail = "me@example.com";

        when(userLookupService.findUserIdByEmail(myEmail)).thenReturn(USER_ID);

        ShareRequest request = new ShareRequest();
        request.setTargetEmail(myEmail);
        request.setRole(Role.VIEWER);

        assertThatThrownBy(() -> dashboardService.shareFolder(folder.getFolderId(), USER_ID, request))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("Should throw if user already has access")
    void shouldThrowIfUserAlreadyHasAccess() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Already Shared");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.VIEWER, USER_ID, "Already Shared");

        String targetEmail = "existing@example.com";
        when(userLookupService.findUserIdByEmail(targetEmail)).thenReturn(OTHER_USER_ID);

        ShareRequest request = new ShareRequest();
        request.setTargetEmail(targetEmail);
        request.setRole(Role.CONTRIBUTOR);

        assertThatThrownBy(() -> dashboardService.shareFolder(folder.getFolderId(), USER_ID, request))
                .isInstanceOf(UserAlreadyHasAccessException.class);
    }

    @Test
    @DisplayName("Should not allow non-owner to share folder")
    void shouldNotAllowNonOwnerToShareFolder() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Not My Folder");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Not My Folder");

        ShareRequest request = new ShareRequest();
        request.setTargetEmail("someone@example.com");
        request.setRole(Role.VIEWER);

        assertThatThrownBy(() -> dashboardService.shareFolder(folder.getFolderId(), OTHER_USER_ID, request))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should get all shared folders for user")
    void shouldGetSharedFolders() {
        String uniqueRecipientId = "recipient-" + java.util.UUID.randomUUID();

        Folder folder1 = testDataFactory.createFolder(USER_ID, "Shared 1");
        Folder folder2 = testDataFactory.createFolder(USER_ID, "Shared 2");

        testDataFactory.createShare(uniqueRecipientId, folder1.getFolderId(), Role.VIEWER, USER_ID, "Shared 1");
        testDataFactory.createShare(uniqueRecipientId, folder2.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Shared 2");

        List<SharedFolderResponse> shared = dashboardService.getSharedFolders(uniqueRecipientId);

        assertThat(shared).hasSize(2);
    }

    @Test
    @DisplayName("Should get folder members including owner")
    void shouldGetFolderMembers() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Team Folder");
        String member1Id = "member-1";

        testDataFactory.createShare(member1Id, folder.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Team Folder");

        when(userLookupService.findEmailByUserId(USER_ID)).thenReturn("owner@example.com");
        when(userLookupService.findEmailByUserId(member1Id)).thenReturn("member1@example.com");

        List<FolderMemberResponse> members = dashboardService.getFolderMembers(folder.getFolderId(), USER_ID);

        assertThat(members).hasSize(2);
    }

    @Test
    @DisplayName("Should remove collaborator from folder")
    void shouldRemoveCollaborator() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Removable");
        testDataFactory.createShare(OTHER_USER_ID, folder.getFolderId(), Role.CONTRIBUTOR, USER_ID, "Removable");

        dashboardService.removeCollaborator(folder.getFolderId(), USER_ID, OTHER_USER_ID);

        assertThat(shareRepository.findAccess(OTHER_USER_ID, folder.getFolderId())).isEmpty();
    }

    @Test
    @DisplayName("Should update share token")
    void shouldUpdateShareToken() {
        Folder folder = testDataFactory.createFolder(USER_ID, "Token Update");
        String oldToken = folder.getShareToken();

        ShareTokenResponse response = dashboardService.updateShareToken(folder.getFolderId(), USER_ID);

        assertThat(response.getToken()).isNotEqualTo(oldToken);
        Folder updated = folderRepository.findById(folder.getFolderId());
        assertThat(updated.getShareToken()).isEqualTo(response.getToken());
    }

    // ==================== HELPERS ====================

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