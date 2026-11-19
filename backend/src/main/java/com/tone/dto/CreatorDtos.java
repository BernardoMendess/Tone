package com.tone.dto;

import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.PayoutStatus;
import com.tone.domain.enums.PickupType;
import com.tone.domain.enums.ProductType;
import com.tone.validation.HttpsUrl;
import com.tone.validation.PixKey;
import jakarta.validation.constraints.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class CreatorDtos {
    private CreatorDtos() {}

    public record CreatorApplicationRequest(
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @NotBlank @Size(min = 10, max = 2000) String bio,
            @NotBlank @Size(max = 255) String mainGear,
            @Size(max = 500) @HttpsUrl String portfolioDemoUrl,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"youtube.com", "www.youtube.com", "youtu.be"}) String youtubeUrl,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"instagram.com", "www.instagram.com"}) String instagramUrl,
            @Size(max = 500) @HttpsUrl String websiteUrl,
            @NotBlank @Size(max = 180) @PixKey String pixKey
    ) {}

    public record UpdateCreatorProfileRequest(
            @NotBlank @Size(min = 2, max = 120) String displayName,
            @Pattern(regexp = "^[a-z0-9-]+$", message = "Slug deve conter apenas letras minúsculas, números e hífens.")
            @Size(min = 3, max = 120) String slug,
            @Size(max = 2000) String bio,
            @Size(max = 500) @HttpsUrl String avatarUrl,
            @Size(max = 255) String mainGear,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"youtube.com", "www.youtube.com", "youtu.be"}) String youtubeUrl,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"instagram.com", "www.instagram.com"}) String instagramUrl,
            @Size(max = 500) @HttpsUrl String websiteUrl,
            @NotBlank @Size(max = 180) @PixKey String pixKey
    ) {}

    public record CreatorProfileDto(
            UUID id,
            UUID userId,
            String userFullName,
            String userEmail,
            String displayName,
            String slug,
            String bio,
            String avatarUrl,
            String mainGear,
            String youtubeUrl,
            String instagramUrl,
            String websiteUrl,
            String portfolioDemoUrl,
            String pixKey,
            CreatorStatus status,
            String rejectionReason,
            Instant reviewedAt,
            Instant createdAt
    ) {}

    public record CreateProductForm(
            @NotBlank @Size(min = 3, max = 180) String title,
            @NotBlank @Size(min = 10, max = 4000) String description,
            @NotNull @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @NotNull ProductType productType,
            @NotBlank @Size(max = 80) String genre,
            @NotBlank @Size(max = 120) String targetGear,
            @NotBlank @Size(max = 120) String sourceGear,
            @NotNull PickupType pickupType,
            @Size(max = 500) String tags,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"youtube.com", "www.youtube.com", "youtu.be"}) String videoUrl,
            @Pattern(regexp = "^\\d+\\.\\d+\\.\\d+$", message = "Versão deve seguir SemVer (ex.: 1.0.0).") String version
    ) {}

    public record UpdateProductRequest(
            @Size(min = 3, max = 180) String title,
            @Size(min = 10, max = 4000) String description,
            @DecimalMin("1.00") @Digits(integer = 10, fraction = 2) BigDecimal price,
            @Size(max = 80) String genre,
            @Size(max = 120) String targetGear,
            @Size(max = 120) String sourceGear,
            PickupType pickupType,
            @Size(max = 500) String tags,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"youtube.com", "www.youtube.com", "youtu.be"}) String videoUrl,
            Boolean active
    ) {}

    public record CreateBundleRequest(
            @NotBlank @Size(min = 3, max = 180) String title,
            @NotBlank @Size(min = 10, max = 4000) String description,
            @NotNull @DecimalMin("5.00") @Digits(integer = 10, fraction = 2) BigDecimal promotionalPrice,
            @NotEmpty @Size(min = 2, max = 20) List<UUID> includedProductIds,
            @Size(max = 500) String tags,
            @Size(max = 500) @HttpsUrl(allowedHosts = {"youtube.com", "www.youtube.com", "youtu.be"}) String videoUrl
    ) {}

    public record SaleRecordDto(
            UUID orderId,
            String orderCode,
            String productTitle,
            String buyerName,
            BigDecimal grossAmount,
            BigDecimal platformFeeAmount,
            BigDecimal creatorNetAmount,
            BigDecimal takeRatePercent,
            Instant paidAt
    ) {}

    public record MonthlyRevenuePoint(
            String month,
            BigDecimal gross,
            BigDecimal net,
            int salesCount
    ) {}

    public record PayoutDto(
            UUID id,
            BigDecimal amount,
            String pixKey,
            PayoutStatus status,
            Instant requestedAt,
            Instant completedAt
    ) {}

    public record RequestPayoutRequest(
            @NotNull @DecimalMin("10.00") @Digits(integer = 10, fraction = 2) BigDecimal amount
    ) {}

    public record CreatorDashboardDto(
            int totalSalesCount,
            int activeProductsCount,
            BigDecimal grossRevenue,
            BigDecimal platformFeeRetained,
            BigDecimal netRevenueTotal,
            BigDecimal totalWithdrawn,
            BigDecimal heldBalance,
            BigDecimal availableBalance,
            BigDecimal currentTakeRatePercent,
            List<MonthlyRevenuePoint> monthlySeries,
            List<SaleRecordDto> recentSales,
            List<PayoutDto> payouts
    ) {}
}
