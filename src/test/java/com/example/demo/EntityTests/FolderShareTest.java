package com.example.demo.EntityTests;

import com.example.demo.entity.FolderShare;
import com.example.demo.enums.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class FolderShareTest {

    @Test
    @DisplayName("create - Sollte Share-Eintrag korrekt mappen")
    void create_shouldMapFieldsCorrectly() {
        // Given
        String targetUserId = "target-user-99";
        String folderId = "folder-1";
        String ownerId = "owner-user-1";
        String folderName = "Shared Stuff";
        Role role = Role.CONTRIBUTOR;

        // When
        FolderShare share = FolderShare.create(targetUserId, folderId, ownerId, folderName, role);

        // Then
        assertAll("FolderShare Checks",
                () -> assertThat(share.getUserId()).isEqualTo(targetUserId),
                () -> assertThat(share.getFolderId()).isEqualTo(folderId),
                () -> assertThat(share.getOwnerId()).isEqualTo(ownerId),
                () -> assertThat(share.getFolderName()).isEqualTo(folderName),
                () -> assertThat(share.getRole()).isEqualTo(role)
        );
    }
}