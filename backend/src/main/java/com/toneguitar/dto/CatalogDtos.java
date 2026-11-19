package com.toneguitar.dto;

import com.toneguitar.domain.enums.PickupType;
import com.toneguitar.domain.enums.ProductType;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CatalogDtos {
    private CatalogDtos() {}

    public record CreatorSummary(
            UUID id,
            String displayName,
            String slug,
            String avatarUrl,
            String mainGear
    ) {}

    public record ProductCardDto(
            UUID id,
            String title,
            String slug,
            String description,
            BigDecimal price,
            BigDecimal originalBundlePrice,
            Integer discountPercent,
            ProductType productType,
            String genre,
            String targetGear,
            String sourceGear,
            PickupType pickupType,
            List<String> tags,
            String demoAudioUrl,
            Integer demoDurationSeconds,
            BigDecimal demoLoudnessLufs,
            Integer demoBitrateKbps,
            String currentVersion,
            BigDecimal averageRating,
            int reviewCount,
            int salesCount,
            int includedCount,
            CreatorSummary creator,
            Instant createdAt
    ) {}

    public record ProductVersionDto(
            UUID id,
            String versionNumber,
            String changelog,
            String originalFilename,
            long fileSizeBytes,
            Instant createdAt
    ) {}

    public record ReviewDto(
            UUID id,
            UUID userId,
            String userName,
            int rating,
            String comment,
            boolean verifiedPurchase,
            Instant createdAt,
            Instant updatedAt
    ) {}

    public record CreateReviewRequest(
            @Min(1) @Max(5) int rating,
            @Size(max = 2000) String comment
    ) {}

    public record ProductDetailDto(
            ProductCardDto product,
            String videoUrl,
            List<ProductVersionDto> versions,
            List<ProductCardDto> bundleItems,
            List<ReviewDto> reviews,
            boolean ownedByCurrentUser,
            boolean wishlistedByCurrentUser,
            boolean canReview
    ) {}

    public record FilterOptionsDto(
            List<String> genres,
            List<String> targetGears,
            List<String> sourceGears,
            List<PickupType> pickupTypes,
            List<ProductType> productTypes
    ) {}

    public record PublicCreatorProfileDto(
            UUID id,
            String displayName,
            String slug,
            String bio,
            String avatarUrl,
            String mainGear,
            String youtubeUrl,
            String instagramUrl,
            String websiteUrl,
            int totalProducts,
            int totalSales,
            BigDecimal averageRating,
            List<ProductCardDto> products
    ) {}
}
