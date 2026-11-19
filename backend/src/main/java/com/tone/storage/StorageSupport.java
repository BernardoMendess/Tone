package com.tone.storage;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class StorageSupport {

    private StorageSupport() {}

    public static String sanitizeFilename(String name) {
        if (name == null || name.isBlank()) {
            return "patch.bin";
        }
        String base = name.replace('\\', '/');
        int slash = base.lastIndexOf('/');
        if (slash >= 0) {
            base = base.substring(slash + 1);
        }
        String safe = base.replaceAll("[^a-zA-Z0-9._-]", "_");
        return safe.isBlank() || safe.equals(".") || safe.equals("..") ? "patch.bin" : safe;
    }

    public static MessageDigest newSha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM.", e);
        }
    }

    public static String toHex(byte[] digest) {
        return HexFormat.of().formatHex(digest);
    }

    public static String sha256Hex(byte[] content) {
        MessageDigest md = newSha256();
        return toHex(md.digest(content));
    }
}
