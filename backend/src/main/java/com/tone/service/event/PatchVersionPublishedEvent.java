package com.tone.service.event;

import java.util.UUID;

public record PatchVersionPublishedEvent(
        UUID productId,
        String productTitle,
        String creatorDisplayName,
        String versionNumber,
        String changelog
) {}
