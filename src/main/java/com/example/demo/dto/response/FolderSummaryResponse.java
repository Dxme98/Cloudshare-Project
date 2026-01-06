package com.example.demo.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FolderSummaryResponse {
    private String id;
    private String name;
    private String createdAt;
    private long fileCount;
    private String shareToken;
}
