package com.example.demo.service;

import com.example.demo.exceptions.*;
import com.example.demo.model.*;
import com.example.demo.repository.FolderRepository;
import com.example.demo.repository.FolderShareRepository;
import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final StorageCoreService storageCore;
    private final FolderRepository folderRepository;
    private final UserLookupService userLookupService;
    private final FolderShareRepository folderShareRepository;

    // Ggf. höheres Limit für eingeloggte User
    private static final long MAX_USER_FOLDER_SIZE = 1024L * 1024 * 1024; // 1 GB

    // --- FOLDER MANAGEMENT ---

    public FolderInitResponse createPermanentFolder(String userId, String folderName) {
        String folderId = UUID.randomUUID().toString();

        Folder folder = Folder.builder()
                .folderId(folderId)
                .folderName(folderName != null ? folderName : "New Folder")
                .type(FolderType.PERMANENT)
                .userId(userId)
                .shareToken(UUID.randomUUID().toString())
                .ownerToken(UUID.randomUUID().toString())
                .createdAt(Instant.now().toString())
                .ttl(null) // Nie löschen
                .build();

        folderRepository.save(folder);
        return FolderMapper.toInitResponse(folder);
    }

    public List<FolderSummaryDTO> getMyFolders(String userId) {
        List<Folder> folders =  folderRepository.findAllByUserId(userId);

        return folders.stream().map(folder -> {
            // Hier berechnen wir die Anzahl der Dateien pro Ordner, can be more efficient
            long count = storageCore.calculateCurrentFolderSize(folder.getFolderId());

            return FolderSummaryDTO.builder()
                    .id(folder.getFolderId())
                    .name(folder.getFolderName())
                    .createdAt(folder.getCreatedAt())
                    .fileCount(count)
                    .build();
        }).toList();
    }

    /**
     * Öffnet einen spezifischen Ordner und zeigt den Inhalt an.
     * Prüft, ob der Ordner dem User gehört.
     */
    public FolderResponse openFolder(String folderId, String userId) {
        // 1. Hole den Ordner (Metadaten)
        // Wir nutzen findById direkt, da wir die Owner-Prüfung manuell machen wollen
        Folder folder = folderRepository.findById(folderId);

        String role;

        // 2. Prüfen: Bin ich der Owner?
        if (folder.getUserId().equals(userId)) {
            role = "OWNER";
        } else {
            // 3. Wenn nicht Owner: Habe ich einen Shared-Eintrag?
            // Wir nutzen deine existierende Repository-Methode!
            FolderShare share = folderShareRepository.findAccess(userId, folderId)
                    .orElseThrow(() -> new AccessDeniedException(
                            "Zugriff verweigert. Dieser Ordner wurde nicht mit dir geteilt."
                    ));

            // Hole die Rolle aus dem Share-Objekt (VIEWER oder CONTRIBUTOR)
            role = share.getRole().name();
        }

        // 4. Dateien laden (via Core Service)
        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);

        // 5. Response bauen
        return FolderMapper.toResponse(folder, files, role);
    }

    // --- FILE OPERATIONS ---

    public String uploadFile(String folderId, String userId, MultipartFile file) {
        Folder folder = findAndValidateOwner(folderId, userId);

        long used = storageCore.calculateCurrentFolderSize(folderId);
        if (used + file.getSize() > MAX_USER_FOLDER_SIZE) {
            throw new StorageLimitExceededException(used, MAX_USER_FOLDER_SIZE);
        }

        return storageCore.uploadPhysicalFile(folderId, file);
    }

    public S3Resource downloadFile(String folderId, String fileId, String userId) {
        // Hier könnte man erweitern: Darf User downloaden? (Owner oder "Shared With Me")
        findAndValidateOwner(folderId, userId);

        return storageCore.downloadFile(fileId);
    }

    public void deleteFile(String folderId, String fileId, String userId) {
        findAndValidateOwner(folderId, userId);

        // Sicherstellen, dass File im Folder ist
        FileMetadata meta = storageCore.getFileMetadata(fileId);
        if (!meta.getFolderId().equals(folderId)) throw new InvalidTokenException();

        storageCore.deletePhysicalFile(fileId);
    }

    public void deleteFolder(String folderId, String userId) {
        findAndValidateOwner(folderId, userId);

        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);
        files.forEach(storageCore::deletePhysicalFile);

        folderRepository.delete(folderId);
    }

    public void shareFolder(String folderId, String ownerId, ShareRequest shareRequest) {
        Folder folder = findAndValidateOwner(folderId, ownerId);

        String targetEmail = shareRequest.getTargetEmail();
        Role role = shareRequest.getRole();

        String targetUserId = userLookupService.findUserIdByEmail(targetEmail);

        if (targetUserId.equals(ownerId)) {
            throw new IllegalArgumentException("Du kannst Ordner nicht mit dir selbst teilen.");
        }

        FolderShare folderShare = FolderShare.builder()
                .folderId(folderId)
                .role(role)
                .ownerId(ownerId)
                .folderName(folder.getFolderName())
                .userId(targetUserId)
                .build();

        folderShareRepository.save(folderShare);

        log.info("Folder {} erfolgreich geteilt von {} an {} ({})",
                folderId, ownerId, targetEmail, role);
    }

    // hier korrektes dto bauen
    public List<FolderShare> getSharedFolders(String userId) {
        return folderShareRepository.findByUserId(userId)
                .items()
                .stream()
                .toList();
    }

    // --- HELPERS ---

    private Folder findAndValidateOwner(String folderId, String userId) {
        // 1. Schritt: Wir suchen exakt den Ordner, um den es geht.
        // Das Repository wirft bereits FolderNotFoundException, wenn die ID nicht existiert.
        Folder folder = folderRepository.findById(folderId);

        // 2. Schritt: Wir prüfen, ob der User, der die Anfrage stellt, auch der Besitzer ist.
        // Sicherheitshalber ein Null-Check, falls userId im Objekt null sein sollte.
        if (folder.getUserId() == null || !folder.getUserId().equals(userId)) {
            log.warn("Security Alert: User {} versuchte auf Ordner {} zuzugreifen (Owner: {})",
                    userId, folderId, folder.getUserId());
            throw new AccessDeniedException("Dieser Ordner gehört dir nicht.");
        }

        return folder;
    }
}