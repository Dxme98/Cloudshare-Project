package com.example.demo.mapper;

import com.example.demo.dto.response.FolderInitResponse;
import com.example.demo.entity.FileMetadata;
import com.example.demo.entity.Folder;
import com.example.demo.dto.response.FolderResponse;
import com.example.demo.dto.response.FolderSummaryResponse;

import java.util.ArrayList;
import java.util.List;

public class FolderMapper {

    public static FolderInitResponse toInitResponse(Folder folder) {
        if (folder == null) return null;

        FolderInitResponse response = new FolderInitResponse();
        response.setFolderId(folder.getFolderId());
        response.setOwnerToken(folder.getOwnerToken());
        response.setShareToken(folder.getShareToken());

        return response;
    }


    public static FolderResponse toResponse(Folder folder, List<FileMetadata> fileMetadataList, String role) {
        if (folder == null) return null;


        FolderResponse response = new FolderResponse();
        response.setFolderId(folder.getFolderId());
        response.setFolderName(folder.getFolderName());
        response.setType(folder.getType().toString().toLowerCase());
        response.setRole(role);
        response.setShareToken(folder.getShareToken());

        if (fileMetadataList == null) {
            response.setFileMetadataList(new ArrayList<>());
        } else {
            response.setFileMetadataList(fileMetadataList);
        }

        boolean isOwner = "OWNER".equals(role);
        response.setOwner(isOwner);

        return response;
    }


    public static FolderSummaryResponse toSummaryDto(Folder folder, long count) {
        return FolderSummaryResponse.builder()
                .id(folder.getFolderId())
                .name(folder.getFolderName())
                .shareToken(folder.getShareToken())
                .createdAt(folder.getCreatedAt())
                .fileCount(count)
                .build();
    }
}