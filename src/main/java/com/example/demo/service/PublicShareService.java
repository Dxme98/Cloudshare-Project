package com.example.demo.service;

import com.example.demo.dto.response.FileUploadResponse;
import com.example.demo.dto.response.FolderInitResponse;
import com.example.demo.dto.response.FolderResponse;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.Folder;
import com.example.demo.enums.FolderType;
import com.example.demo.enums.Role;
import com.example.demo.exceptions.custom.InvalidTokenException;
import com.example.demo.exceptions.custom.StorageLimitExceededException;
import com.example.demo.mapper.FolderMapper;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.FolderRepository;
import io.awspring.cloud.s3.S3Resource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;


@Service
@RequiredArgsConstructor
@Slf4j
public class PublicShareService {

    private final FolderRepository folderRepository;
    private final FileStorageService fileStorage;
    private final FileMetadataRepository fileMetadataRepository;

    private static final long MAX_TEMP_FOLDER_SIZE = 500 * 1024 * 1024; // 500 MB

    public FolderInitResponse initializeFolder() {
        Folder folder = Folder.builder()
                .folderId(UUID.randomUUID().toString())
                .ownerToken(UUID.randomUUID().toString())
                .shareToken(UUID.randomUUID().toString())
                .ttl(Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond())
                .folderName("Temporary Folder")
                .type(FolderType.TEMPORARY)
                .build();

        folderRepository.save(folder);
        return FolderMapper.toInitResponse(folder);
    }

    public FolderResponse openFolder(String token, String folderId) {
        Folder folder = folderRepository.findById(folderId);
        Role role = validateTokenAndGetRole(folder, token);

        List<FileMetadata> files = fileStorage.getFilesInFolder(folderId);
        return FolderMapper.toResponse(folder, files, role.name());
    }

    public FileUploadResponse uploadFileWithToken(String folderId, String token, MultipartFile file) {
        Folder folder = folderRepository.findById(folderId);
        requireOwnerToken(folder, token);

        // Quota
        long currentSize = fileMetadataRepository.sumFileSizesByFolderId(folderId);
        if (currentSize + file.getSize() > MAX_TEMP_FOLDER_SIZE) {
            throw new StorageLimitExceededException(currentSize, MAX_TEMP_FOLDER_SIZE);
        }

        FileMetadata metadata = fileStorage.uploadFile(folderId, file);
        return FileUploadResponse.create(metadata.getFileId());
    }

    public S3Resource downloadFile(String folderId, String fileId, String token) {
        Folder folder = folderRepository.findById(folderId);
        validateTokenAndGetRole(folder, token);

        FileMetadata metadata = fileStorage.getMetadata(fileId);
        if (!metadata.getFolderId().equals(folderId)) {
            throw new InvalidTokenException();
        }

        return fileStorage.downloadFile(fileId);
    }

    public void deleteFileWithToken(String folderId, String fileId, String token) {
        Folder folder = folderRepository.findById(folderId);
        requireOwnerToken(folder, token);

        FileMetadata metadata = fileStorage.getMetadata(fileId);
        if (!metadata.getFolderId().equals(folderId)) {
            throw new InvalidTokenException();
        }

        fileStorage.deleteFile(fileId);
    }

    // --- TOKEN VALIDATION ---

    private Role validateTokenAndGetRole(Folder folder, String token) {
        if (folder.getOwnerToken().equals(token)) return Role.OWNER;
        if (folder.getShareToken().equals(token)) return Role.VIEWER;
        throw new InvalidTokenException();
    }

    private void requireOwnerToken(Folder folder, String token) {
        if (!folder.getOwnerToken().equals(token)) {
            throw new InvalidTokenException();
        }
    }
}