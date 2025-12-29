package com.example.demo;

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


    public static FolderResponse toResponse(Folder folder, String inputToken, List<FileMetadata> fileMetadataList) {
        if (folder == null) return null;


        FolderResponse response = new FolderResponse();
        response.setFolderId(folder.getFolderId());
        response.setFolderName(folder.getFolderName());

        if (fileMetadataList == null) {
            response.setFileMetadataList(new ArrayList<>());
        } else {
            response.setFileMetadataList(fileMetadataList);
        }

        response.setOwner(inputToken != null && inputToken.equals(folder.getOwnerToken()));

        if(response.isOwner()) response.setShareToken(folder.getShareToken());

        return response;
    }
}