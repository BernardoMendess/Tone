package com.tone.storage;

public record StoredObject(
        String storageKey,
        long sizeBytes,
        String sha256Checksum
) {}
