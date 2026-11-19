package com.tone.storage;

import java.time.Instant;

public record PresignedDownload(
        String url,
        String filename,
        Instant expiresAt,
        String storageMode
) {}
