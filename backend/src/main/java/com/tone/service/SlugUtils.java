package com.tone.service;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Predicate;

public final class SlugUtils {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] SUFFIX_CHARS = "abcdefghjkmnpqrstuvwxyz23456789".toCharArray();

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

    public static String generateUnique(String source, Predicate<String> existsFn) {
        String base = toSlug(source);
        if (!existsFn.test(base)) {
            return base;
        }
        for (int attempt = 0; attempt < 5; attempt++) {
            String candidate = base + "-" + randomSuffix(4);
            if (!existsFn.test(candidate)) {
                return candidate;
            }
        }
        return base + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String withRandomSuffix(String base) {
        return toSlug(base) + "-" + randomSuffix(5);
    }

    private static String randomSuffix(int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = SUFFIX_CHARS[RNG.nextInt(SUFFIX_CHARS.length)];
        }
        return new String(buf);
    }
}
