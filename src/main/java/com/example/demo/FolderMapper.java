package com.example.demo;

import java.util.ArrayList;
import java.util.List;

public class FolderMapper {


    public static FolderResponse toResponse(Folder folder, String inputToken, List<FileMetadata> fileMetadataList) {
        if (folder == null) {
            return null;
        }

        FolderResponse response = new FolderResponse();
        response.setFolderId(folder.getFolderId());
        response.setFolderName(folder.getFolderName());

        if (fileMetadataList == null) {
            response.setFileMetadataList(new ArrayList<>());
        } else {
            response.setFileMetadataList(fileMetadataList);
        }

        response.setOwnerToken(folder.getOwnerToken());
        response.setShareToken(folder.getShareToken());

        response.setOwner(inputToken != null && inputToken.equals(folder.getOwnerToken()));

        return response;
    }
}