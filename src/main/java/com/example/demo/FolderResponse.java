package com.example.demo;

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
    private String type = "temporary";
    private String shareToken;

    @JsonProperty("files")
    private List<FileMetadata> fileMetadataList;
}
