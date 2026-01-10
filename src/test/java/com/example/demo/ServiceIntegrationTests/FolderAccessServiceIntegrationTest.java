package com.example.demo.ServiceIntegrationTests;

import com.example.demo.TestHelper.TestDataFactory;
import com.example.demo.entity.Folder;
import com.example.demo.enums.Role;
import com.example.demo.exceptions.custom.FolderNotFoundException;
import com.example.demo.service.FolderAccessService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FolderAccessServiceIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private FolderAccessService accessService;

    @Autowired
    private TestDataFactory testDataFactory;

    private static final String OWNER_ID = "user-owner";
    private static final String CONTRIBUTOR_ID = "user-contributor";
    private static final String VIEWER_ID = "user-viewer";
    private static final String STRANGER_ID = "user-stranger";

    @Test
    @DisplayName("OWNER should have full access (Owner, Contributor, Viewer)")
    void ownerShouldHaveFullAccess() {
        // Given
        Folder folder = testDataFactory.createFolder(OWNER_ID, "Owner Folder");

        // When & Then
        assertThat(accessService.getUserRole(OWNER_ID, folder.getFolderId())).isEqualTo(Role.OWNER);

        accessService.requireOwner(OWNER_ID, folder.getFolderId());

        accessService.requireRole(OWNER_ID, folder.getFolderId(), Role.OWNER);
        accessService.requireRole(OWNER_ID, folder.getFolderId(), Role.CONTRIBUTOR);
        accessService.requireRole(OWNER_ID, folder.getFolderId(), Role.VIEWER);
    }

    @Test
    @DisplayName("CONTRIBUTOR should have read/write but NO owner access")
    void contributorShouldHaveRestrictedAccess() {
        // Given
        Folder folder = testDataFactory.createFolder(OWNER_ID, "Project Folder");
        testDataFactory.createShare(CONTRIBUTOR_ID, folder.getFolderId(), Role.CONTRIBUTOR, OWNER_ID, "Project Folder");

        // When & Then
        assertThat(accessService.getUserRole(CONTRIBUTOR_ID, folder.getFolderId())).isEqualTo(Role.CONTRIBUTOR);

        accessService.requireRole(CONTRIBUTOR_ID, folder.getFolderId(), Role.CONTRIBUTOR);
        accessService.requireRole(CONTRIBUTOR_ID, folder.getFolderId(), Role.VIEWER);

        assertThatThrownBy(() ->
                accessService.requireRole(CONTRIBUTOR_ID, folder.getFolderId(), Role.OWNER))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("erfordert OWNER Rechte");

        assertThatThrownBy(() ->
                accessService.requireOwner(CONTRIBUTOR_ID, folder.getFolderId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Nur der Besitzer");
    }

    @Test
    @DisplayName("VIEWER should have read-only access")
    void viewerShouldHaveReadOnlyAccess() {
        // Given
        Folder folder = testDataFactory.createFolder(OWNER_ID, "ReadOnly Folder");
        testDataFactory.createShare(VIEWER_ID, folder.getFolderId(), Role.VIEWER, OWNER_ID, "ReadOnly Folder");

        // When & Then
        assertThat(accessService.getUserRole(VIEWER_ID, folder.getFolderId())).isEqualTo(Role.VIEWER);

        accessService.requireRole(VIEWER_ID, folder.getFolderId(), Role.VIEWER);

        assertThatThrownBy(() ->
                accessService.requireRole(VIEWER_ID, folder.getFolderId(), Role.CONTRIBUTOR))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("erfordert CONTRIBUTOR Rechte");

        assertThatThrownBy(() ->
                accessService.requireRole(VIEWER_ID, folder.getFolderId(), Role.OWNER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("STRANGER should have NO access at all")
    void strangerShouldHaveNoAccess() {
        // Given
        Folder folder = testDataFactory.createFolder(OWNER_ID, "Private Folder");

        // When & Then
        assertThatThrownBy(() ->
                accessService.getUserRole(STRANGER_ID, folder.getFolderId()))
                .isInstanceOf(AccessDeniedException.class)
                .hasMessageContaining("Keine Berechtigung");

        assertThatThrownBy(() ->
                accessService.requireRole(STRANGER_ID, folder.getFolderId(), Role.VIEWER))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    @DisplayName("Should handle non-existent folder correctly")
    void shouldHandleMissingFolder() {


        String nonExistentId = "folder-" + UUID.randomUUID();

        assertThatThrownBy(() ->
                accessService.getUserRole(OWNER_ID, nonExistentId))
                .isInstanceOf(FolderNotFoundException.class);
    }

    @Test
    @DisplayName("requireOwner should be strictly checked against DB")
    void requireOwnerShouldBeStrict() {
        Folder folder = testDataFactory.createFolder(OWNER_ID, "Strict Check");

        assertThatThrownBy(() -> accessService.requireOwner(CONTRIBUTOR_ID, folder.getFolderId()))
                .isInstanceOf(AccessDeniedException.class);

        accessService.requireOwner(OWNER_ID, folder.getFolderId());
    }
}