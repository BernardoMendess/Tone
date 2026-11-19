package com.tone.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;

public final class SlugUtils {
    private SlugUtils() {}

    public static String toSlug(String input) {
        if (input == null || input.isBlank()) {
            return "item-" + UUID.randomUUID().toString().substring(0, 8);
        }
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{InCombiningDiacriticalMarks}+", "");
        String slug = normalized.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-+|-+$", "");
        return slug.isBlank() ? "item-" + UUID.randomUUID().toString().substring(0, 8) : slug;
    }
}
