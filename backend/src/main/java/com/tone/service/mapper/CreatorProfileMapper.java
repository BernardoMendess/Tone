package com.tone.service.mapper;

import com.tone.domain.entity.CreatorProfile;
import com.tone.dto.CreatorDtos;

public final class CreatorProfileMapper {

    private CreatorProfileMapper() {}

    public static CreatorDtos.CreatorProfileDto toDto(CreatorProfile c) {
        return new CreatorDtos.CreatorProfileDto(
                c.getId(),
                c.getUser().getId(),
                c.getUser().getFullName(),
                c.getUser().getEmail(),
                c.getDisplayName(),
                c.getSlug(),
                c.getBio(),
                c.getAvatarUrl(),
                c.getMainGear(),
                c.getYoutubeUrl(),
                c.getInstagramUrl(),
                c.getWebsiteUrl(),
                c.getPortfolioDemoUrl(),
                c.getPixKey(),
                c.getStatus(),
                c.getRejectionReason(),
                c.getReviewedAt(),
                c.getCreatedAt()
        );
    }
}
