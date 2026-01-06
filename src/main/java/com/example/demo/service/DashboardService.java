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

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final StorageCoreService storageCore;
    private final FolderRepository folderRepository;
    private final UserLookupService userLookupService;
    private final FolderShareRepository folderShareRepository;

    private static final long MAX_USER_FOLDER_SIZE = 1024L * 1024 * 1024; // 1 GB

    // --- FOLDER MANAGEMENT ---

    public FolderInitResponse createPermanentFolder(String userId, String folderName) {
        Folder folder = Folder.createPermanentFolder(userId, folderName);
        folderRepository.save(folder);

        log.info("Permanent folder created: '{}' (ID: {}) for User: {}", folderName, folder.getFolderId(), userId);

        return FolderMapper.toInitResponse(folder);
    }

    public List<FolderSummaryDTO> getMyFolders(String userId) {
        log.debug("Fetching folder list for User: {}", userId);
        List<Folder> folders =  folderRepository.findAllByUserId(userId);

        return folders.stream().map(folder -> {
            long count = storageCore.getFileCount(folder.getFolderId());
            return FolderMapper.toSummaryDto(folder, count);
        }).toList();
    }

    public FolderResponse openFolder(String folderId, String userId) {
        Folder folder = folderRepository.findById(folderId);

        // Rolle ermitteln und loggen (hilft beim Debuggen von Berechtigungen)
        Role role = checkForSharedAccessReturnsRole(userId, folder);
        log.debug("User {} accessing folder {} with Role: {}", userId, folderId, role);

        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);

        return FolderMapper.toResponse(folder, files, role.toString());
    }

    public List<FolderMemberDTO> getFolderMembers(String folderId, String userId) {
        findAndValidateOwner(folderId, userId);

        return folderShareRepository.findByFolderId(folderId)
                .stream()
                .flatMap(page -> page.items().stream())
                .map(share -> {
                    String email = userLookupService.findEmailByUserId(share.getUserId());

                    if (email == null) email = "Unknown User";

                    return FolderMemberDTO.builder()
                            .userId(share.getUserId())
                            .email(email)
                            .role(share.getRole())
                            .build();
                })
                .toList();
    }

    public void removeCollaborator(String folderId, String ownerId, String targetUserId) {
        findAndValidateOwner(folderId, ownerId);

        if (ownerId.equals(targetUserId)) {
            throw new IllegalArgumentException("Du kannst dich nicht selbst aus der Mitgliederliste entfernen.");
        }

        folderShareRepository.deleteShare(targetUserId, folderId);

        log.info("Collaborator removed. Folder: {}, Owner: {}, Removed User: {}",
                folderId, ownerId, targetUserId);
    }

    public String updateShareToken(String folderId, String userId) {
        Folder folder = findAndValidateOwner(folderId, userId);

        String newToken = folder.updateShareToken();

        folderRepository.save(folder);

        log.info("Share token regenerated for folder {}. User: {}", folderId, userId);

        return newToken;
    }

    // --- FILE OPERATIONS ---

    public String uploadFile(String folderId, String userId, MultipartFile file) {
        Folder folder = folderRepository.findById(folderId);
        Role role = checkForSharedAccessReturnsRole(userId, folder);

        if (role == Role.VIEWER) {
            log.warn("Access Denied: Viewer {} tried to upload file to folder {}", userId, folderId);
            throw new AccessDeniedException("Viewer dürfen keine Dateien hochladen.");
        }

        long used = storageCore.calculateCurrentFolderSize(folderId);
        if (used + file.getSize() > MAX_USER_FOLDER_SIZE) {
            log.warn("Quota exceeded for folder {}: Current: {}, Added: {}, Limit: {}",
                    folderId, used, file.getSize(), MAX_USER_FOLDER_SIZE);
            throw new StorageLimitExceededException(used, MAX_USER_FOLDER_SIZE);
        }

        String fileId = storageCore.uploadPhysicalFile(folderId, file);
        log.info("File uploaded successfully. Name: '{}', Size: {} bytes, User: {}, Folder: {}",
                file.getOriginalFilename(), file.getSize(), userId, folderId);

        return fileId;
    }

    public S3Resource downloadFile(String folderId, String fileId, String userId) {
        Folder folder = folderRepository.findById(folderId);
        checkForSharedAccessReturnsRole(userId, folder); // Berechtigung prüfen

        log.info("File download requested. FileID: {}, User: {}", fileId, userId);
        return storageCore.downloadFile(fileId);
    }

    public void deleteFile(String folderId, String fileId, String userId) {
        Folder folder = folderRepository.findById(folderId);
        Role role = checkForSharedAccessReturnsRole(userId, folder);

        if (role == Role.VIEWER) {
            log.warn("Access Denied: Viewer {} tried to delete file {} in folder {}", userId, fileId, folderId);
            throw new AccessDeniedException("Viewer dürfen keine Dateien löschen.");
        }

        FileMetadata meta = storageCore.getFileMetadata(fileId);
        if (!meta.getFolderId().equals(folderId)) {
            log.error("Security Mismatch: File {} does not belong to Folder {}", fileId, folderId);
            throw new InvalidTokenException();
        }

        storageCore.deletePhysicalFile(fileId);
        log.info("File deleted. FileID: {}, User: {}, Folder: {}", fileId, userId, folderId);
    }

    public void deleteFolder(String folderId, String userId) {
        findAndValidateOwner(folderId, userId);

        List<FileMetadata> files = storageCore.fetchFilesForFolder(folderId);
        int fileCount = files.size();
        files.forEach(storageCore::deletePhysicalFile);

        List<FolderShare> shares = folderShareRepository.findByFolderId(folderId)
                .stream()
                .flatMap(page -> page.items().stream())
                .toList();

        for (FolderShare share : shares) {
            folderShareRepository.deleteShare(share.getUserId(), folderId);
        }
        folderRepository.delete(folderId);

        log.info("Folder deleted. ID: {}, User: {}, Deleted Files: {}, Removed Shares: {}",
                folderId, userId, fileCount, shares.size());
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

        // Wichtiges Audit-Log: Wer hat wem welche Rechte gegeben?
        log.info("Folder shared. FolderID: {}, Owner: {}, Target: {} (ID: {}), Role: {}",
                folderId, ownerId, targetEmail, targetUserId, role);
    }

    public List<FolderShare> getSharedFolders(String userId) {
        log.debug("Fetching shared folders for User: {}", userId);
        return folderShareRepository.findByUserId(userId)
                .items()
                .stream()
                .toList();
    }



    // --- HELPERS ---

    private Folder findAndValidateOwner(String folderId, String userId) {
        Folder folder = folderRepository.findById(folderId);

        if (folder.getUserId() == null || !folder.getUserId().equals(userId)) {
            // WARN level für Security Events ist Best Practice
            log.warn("Security Alert: Unauthorized access attempt. User {} tried to access Owner-only Folder {}", userId, folderId);
            throw new AccessDeniedException("Dieser Ordner gehört dir nicht.");
        }

        return folder;
    }

    private Role checkForSharedAccessReturnsRole(String userId, Folder folder) {
        if (folder.getUserId().equals(userId)) {
            return Role.OWNER;
        } else {
            FolderShare share = folderShareRepository.findAccess(userId, folder.getFolderId())
                    .orElseThrow(() -> {
                        log.warn("Access Denied: User {} has no shared access to Folder {}", userId, folder.getFolderId());
                        return new AccessDeniedException("Zugriff verweigert. Dir fehlen die nötigen Berechtigungen.");
                    });

            return share.getRole();
        }
    }
}