package com.example.demo;

import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FolderResponse {
    private String folderId;
    private String folderName;
    private boolean isOwner = false;
    private List<FileMetadata> fileMetadataList;
}
