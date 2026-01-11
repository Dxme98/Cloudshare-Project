package com.example.demo.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.time.Duration;

@Getter
@RequiredArgsConstructor
public enum FolderType {

    TEMPORARY(500L * 1024 * 1024, Duration.ofHours(24)), // 500 MB, 24h Lebensdauer
    PERMANENT(1024L * 1024 * 1024, null);                // 1 GB, Unendlich (null)

    private final long defaultStorageLimit;
    private final Duration defaultTtlDuration;
}