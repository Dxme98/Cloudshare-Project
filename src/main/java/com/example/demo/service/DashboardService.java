package com.example.demo.service;

import com.example.demo.dto.request.CreateFolderRequest;
import com.example.demo.dto.request.ShareRequest;
import com.example.demo.dto.response.*;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.Folder;
import com.example.demo.entity.FolderShare;
import com.example.demo.enums.FolderType;
import com.example.demo.enums.Role;
import com.example.demo.exceptions.custom.InvalidTokenException;
import com.example.demo.exceptions.custom.StorageLimitExceededException;
import com.example.demo.exceptions.custom.UserAlreadyHasAccessException;
import com.example.demo.mapper.FolderMapper;
import com.example.demo.mapper.SharedFolderMapper;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.FolderRepository;
import com.example.demo.repository.FolderShareRepository;
import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;


@Service
@RequiredArgsConstructor
@Slf4j
public class DashboardService {

    private final FolderRepository folderRepository;
    private final FolderShareRepository shareRepository;
    private final FileStorageService fileStorage;
    private final FolderAccessService accessService;
    private final UserLookupService userLookup;
    private final FileMetadataRepository fileMetadataRepository;

    // --- FOLDER OPERATIONS ---

    public FolderInitResponse createPermanentFolder(String userId, CreateFolderRequest request) {
        Folder folder = Folder.createPermanentFolder(userId, request.getName());
        folderRepository.save(folder);

        log.info("Folder created: {} by User {}", folder.getFolderId(), userId);
        return FolderMapper.toInitResponse(folder);
    }

    public List<FolderSummaryResponse> getMyFolders(String userId) {
        return folderRepository.findAllByUserId(userId).stream()
                .map(FolderMapper::toSummaryDto)
                .toList();
    }

    public FolderResponse openFolder(String folderId, String userId) {
        Folder folder = folderRepository.findById(folderId);
        Role role = accessService.getUserRole(userId, folder);

        List<FileMetadata> files = fileStorage.getFilesInFolder(folderId);
        return FolderMapper.toResponse(folder, files, role.name());
    }

    public void deleteFolder(String folderId, String userId) {
        accessService.requireOwner(userId, folderId);
        folderRepository.delete(folderId);
        log.info("Folder {} deleted by User {}", folderId, userId);
    }

    // --- FILE OPERATIONS ---

    public FileUploadResponse uploadFile(String folderId, String userId, MultipartFile file) {
        accessService.requireRole(userId, folderId, Role.CONTRIBUTOR);

        // Quota Check
        long currentSize = fileMetadataRepository.sumFileSizesByFolderId(folderId);
        if (currentSize + file.getSize() > FolderType.PERMANENT.getDefaultStorageLimit()) {
            throw new StorageLimitExceededException(currentSize, FolderType.PERMANENT.getDefaultStorageLimit());
        }

        FileMetadata metadata = fileStorage.uploadFile(folderId, file);

        folderRepository.incrementFolderStats(folderId, file.getSize());
        return FileUploadResponse.create(metadata.getFileId());
    }

    public S3Resource downloadFile(String folderId, String fileId, String userId) {
        accessService.requireRole(userId, folderId, Role.VIEWER);
        validateFileInFolder(fileId, folderId);
        return fileStorage.downloadFile(fileId);
    }

    public void deleteFile(String folderId, String fileId, String userId) {
        accessService.requireRole(userId, folderId, Role.CONTRIBUTOR);
        validateFileInFolder(fileId, folderId);

        FileMetadata metadata = fileStorage.deleteFile(fileId);
        folderRepository.decrementFolderStats(folderId, metadata.getFileSize());
    }

    // --- SHARING ---

    public void shareFolder(String folderId, String ownerId, ShareRequest request) {
        accessService.requireOwner(ownerId, folderId);
        Folder folder = folderRepository.findById(folderId);

        String targetUserId = userLookup.findUserIdByEmail(request.getTargetEmail());

        if (Objects.equals(targetUserId, ownerId)) {
            throw new IllegalArgumentException("Kann nicht mit sich selbst teilen");
        }

        if(shareRepository.findAccess(targetUserId, folderId).isPresent()) {
            throw new UserAlreadyHasAccessException(targetUserId);
        }

        FolderShare share = FolderShare.create(targetUserId, folderId, ownerId, folder.getFolderName(), request.getRole());

        shareRepository.save(share);
        log.info("Folder {} shared with {} as {}", folderId, targetUserId, request.getRole());
    }

    public List<SharedFolderResponse> getSharedFolders(String userId) {
        return shareRepository.findByUserId(userId)
                .items().stream()
                .map(SharedFolderMapper::toResponse)
                .toList();
    }

    public List<FolderMemberResponse> getFolderMembers(String folderId, String userId) {
        accessService.requireOwner(userId, folderId);

        List<FolderMemberResponse> members = new ArrayList<>();

        // Owner hinzufügen
        String ownerEmail = userLookup.findEmailByUserId(userId);
        members.add(FolderMemberResponse.builder()
                .userId(userId)
                .email(ownerEmail != null ? ownerEmail : "You")
                .role(Role.OWNER)
                .build());

        // Shared Members
        shareRepository.findByFolderId(folderId).stream()
                .flatMap(page -> page.items().stream())
                .map(share -> FolderMemberResponse.builder()
                        .userId(share.getUserId())
                        .email(userLookup.findEmailByUserId(share.getUserId()))
                        .role(share.getRole())
                        .build())
                .forEach(members::add);

        return members;
    }

    public void removeCollaborator(String folderId, String ownerId, String targetUserId) {
        accessService.requireOwner(ownerId, folderId);

        if (Objects.equals(ownerId, targetUserId)) {
            throw new IllegalArgumentException("Kann sich nicht selbst entfernen");
        }

        shareRepository.deleteShare(targetUserId, folderId);
    }

    public ShareTokenResponse updateShareToken(String folderId, String userId) {
        accessService.requireOwner(userId, folderId);
        Folder folder = folderRepository.findById(folderId);

        String newToken = folder.updateShareToken();
        folderRepository.save(folder);

        return ShareTokenResponse.create(newToken);
    }

    // --- HELPERS ---

    private void validateFileInFolder(String fileId, String folderId) {
        FileMetadata metadata = fileStorage.getMetadata(fileId);

        if (!Objects.equals(metadata.getFolderId(), folderId)) {
            throw new InvalidTokenException();
        }
    }
}