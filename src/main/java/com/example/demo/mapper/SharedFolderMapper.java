package com.example.demo.mapper;

import com.example.demo.dto.response.SharedFolderResponse;
import com.example.demo.entity.FolderShare;

public class SharedFolderMapper {

    public static SharedFolderResponse toResponse(FolderShare entity) {
        if (entity == null) return null;


        SharedFolderResponse response = new SharedFolderResponse();

        response.setUserId(entity.getUserId());
        response.setFolderId(entity.getFolderId());
        response.setOwnerId(entity.getOwnerId());
        response.setFolderName(entity.getFolderName());
        response.setRole(entity.getRole());

        return response;
    }
}
