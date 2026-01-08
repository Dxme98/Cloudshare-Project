package com.example.demo.dto.response;

import com.example.demo.entity.FileMetadata;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class FolderResponse {
    private String folderId;
    private String folderName;
    @JsonProperty("isOwner")
    private boolean isOwner = false;
    private String type;
    private String shareToken;
    private Long usedStorage;
    private Long maxStorage;

    @JsonProperty("files")
    private List<FileMetadata> fileMetadataList;

    private String role;
}
