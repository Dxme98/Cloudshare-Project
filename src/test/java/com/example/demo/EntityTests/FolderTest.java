package com.example.demo.EntityTests;

import com.example.demo.entity.Folder;
import com.example.demo.enums.FolderType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertAll;

class FolderTest {

    @Test
    @DisplayName("createPermanentFolder - Sollte 1GB Limit und KEINE TTL haben")
    void createPermanentFolder_shouldInitCorrectly() {
        // Given
        String userId = "user-1";
        String folderName = "My Perm Folder";

        // When
        Folder folder = Folder.createPermanentFolder(userId, folderName);

        // Then
        assertAll("Permanent Folder Defaults",
                () -> assertThat(folder.getFolderId()).isNotNull(),
                () -> assertThat(folder.getShareToken()).isNotNull(),
                () -> assertThat(folder.getUserId()).isEqualTo(userId),
                () -> assertThat(folder.getFolderName()).isEqualTo(folderName),
                () -> assertThat(folder.getType()).isEqualTo(FolderType.PERMANENT),

                // Logik Prüfungen
                () -> assertThat(folder.getOwnerToken()).isNull(),
                () -> assertThat(folder.getMaxStorage()).isEqualTo(1024L * 1024 * 1024), // 1GB
                () -> assertThat(folder.getTtl()).isNull(),
                () -> assertThat(folder.getUsedStorage()).isZero(),
                () -> assertThat(folder.getFileCount()).isZero()
        );
    }

    @Test
    @DisplayName("createTemporaryFolder - Sollte 500MB Limit und TTL in der Zukunft haben")
    void createTemporaryFolder_shouldInitCorrectly() {
        // Given
        String folderName = "Temp Share";

        // When
        Folder folder = Folder.createTemporaryFolder(folderName);

        // Then
        assertAll("Temporary Folder Defaults",
                () -> assertThat(folder.getFolderId()).isNotNull(),
                () -> assertThat(folder.getType()).isEqualTo(FolderType.TEMPORARY),

                () -> assertThat(folder.getOwnerToken()).isNotNull(), // Temp braucht OwnerToken für Admin-Rechte
                () -> assertThat(folder.getMaxStorage()).isEqualTo(500L * 1024 * 1024), // 500 MB

                () -> assertThat(folder.getTtl()).isNotNull(),
                () -> assertThat(folder.getTtl()).isGreaterThan(Instant.now().getEpochSecond()),

                () -> assertThat(folder.getUsedStorage()).isZero(),
                () -> assertThat(folder.getFileCount()).isZero()
        );
    }

    @Test
    @DisplayName("create... - Sollte Fallback-Namen nutzen wenn Name null ist")
    void create_shouldUseFallbackName() {
        Folder pFolder = Folder.createPermanentFolder("u1", null);
        Folder tFolder = Folder.createTemporaryFolder(null);

        assertAll(
                () -> assertThat(pFolder.getFolderName()).isEqualTo("New Folder"),
                () -> assertThat(tFolder.getFolderName()).isEqualTo("New Folder")
        );
    }

    @Test
    @DisplayName("updateShareToken - Sollte neuen Token generieren")
    void updateShareToken_shouldChangeToken() {
        // Given
        Folder folder = Folder.createPermanentFolder("u1", "Test");
        String oldToken = folder.getShareToken();

        // When
        String newToken = folder.updateShareToken();

        // Then
        assertThat(newToken).isNotNull();
        assertThat(newToken).isNotEqualTo(oldToken);
        assertThat(folder.getShareToken()).isEqualTo(newToken);
    }
}