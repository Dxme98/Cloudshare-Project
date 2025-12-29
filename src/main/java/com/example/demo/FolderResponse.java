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
    private boolean isOwner = false;
    private String type = "temporary";

    @JsonProperty("files")
    private List<FileMetadata> fileMetadataList;
}
