package com.tone.domain.entity;

import java.security.SecureRandom;
import java.time.Year;
import java.time.ZoneOffset;

public final class OrderCodeGenerator {

    private static final SecureRandom RNG = new SecureRandom();
    private static final char[] ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();

    private OrderCodeGenerator() {}

    public static String next() {
        char[] suffix = new char[10];
        for (int i = 0; i < suffix.length; i++) {
            suffix[i] = ALPHABET[RNG.nextInt(ALPHABET.length)];
        }
        return "TN-" + Year.now(ZoneOffset.UTC).getValue() + "-" + new String(suffix);
    }
}
