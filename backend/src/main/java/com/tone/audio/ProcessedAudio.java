package com.tone.audio;

import java.math.BigDecimal;

public record ProcessedAudio(
        byte[] bytes,
        String contentType,
        String extension,
        int durationSeconds,
        BigDecimal normalizedLufs,
        int effectiveBitrateKbps,
        String protectionMethod
) {}
