package com.example.demo.TestHelper;

import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.Folder;
import com.example.demo.entity.FolderShare;
import com.example.demo.enums.Role;
import com.example.demo.repository.FileMetadataRepository;
import com.example.demo.repository.FolderRepository;
import com.example.demo.repository.FolderShareRepository;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class TestDataFactory {

    private final FolderRepository folderRepository;
    private final FolderShareRepository shareRepository;
    private final FileMetadataRepository fileMetadataRepository;


    public TestDataFactory(FolderRepository folderRepository,
                           FolderShareRepository shareRepository,
                           FileMetadataRepository fileMetadataRepository) {
        this.folderRepository = folderRepository;
        this.shareRepository = shareRepository;
        this.fileMetadataRepository = fileMetadataRepository;
    }

    public Folder createFolder(String userId, String name) {
        Folder folder = Folder.createPermanentFolder(userId, name);
        folderRepository.save(folder);
        return folder;
    }

    public FolderShare createShare(String userId, String folderId, Role role, String ownerId, String folderName) {
        FolderShare share = FolderShare.builder()
                .userId(userId)
                .folderId(folderId)
                .role(role)
                .ownerId(ownerId)
                .folderName(folderName)
                .build();

        shareRepository.save(share);
        return share;
    }

    public FileMetadata createFile(String folderId, String fileName, long size) {
        FileMetadata file = FileMetadata.builder()
                .fileId(UUID.randomUUID().toString())
                .folderId(folderId)
                .fileName(fileName)
                .s3Key("test/" + fileName)
                .fileSize(size)
                .build();
        file.setUploadDate();
        return fileMetadataRepository.save(file);
    }
}