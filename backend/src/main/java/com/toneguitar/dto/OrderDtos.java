package com.toneguitar.dto;

import com.toneguitar.domain.enums.NotificationType;
import com.toneguitar.domain.enums.OrderStatus;
import com.toneguitar.domain.enums.PaymentMethod;
import com.toneguitar.domain.enums.SplitStatus;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class OrderDtos {
    private OrderDtos() {}

    public record CartItemDto(
            UUID productId,
            CatalogDtos.ProductCardDto product,
            Instant addedAt
    ) {}

    public record CartDto(
            List<CartItemDto> items,
            int count,
            BigDecimal subtotal,
            BigDecimal takeRatePercent
    ) {}

    public record CheckoutRequest(
            @NotNull PaymentMethod paymentMethod,
            String cardLast4
    ) {}

    public record OrderItemDto(
            UUID id,
            UUID productId,
            String productSlug,
            String title,
            String creatorName,
            String purchasedVersion,
            String latestVersion,
            BigDecimal unitPrice,
            BigDecimal platformFeeAmount,
            BigDecimal creatorNetAmount
    ) {}

    public record PaymentSplitDto(
            UUID id,
            UUID creatorId,
            String creatorName,
            BigDecimal grossAmount,
            BigDecimal platformFeeAmount,
            BigDecimal creatorNetAmount,
            BigDecimal takeRatePercent,
            SplitStatus status,
            String gatewaySplitReference
    ) {}

    public record OrderDto(
            UUID id,
            String orderCode,
            OrderStatus status,
            PaymentMethod paymentMethod,
            BigDecimal totalAmount,
            BigDecimal platformFeeTotal,
            BigDecimal creatorNetTotal,
            String gatewayTransactionId,
            String pixCopyPaste,
            Instant pixExpiresAt,
            Instant paidAt,
            Instant createdAt,
            List<OrderItemDto> items,
            List<PaymentSplitDto> splits
    ) {}

    public record PaymentWebhookRequest(
            String gatewayTransactionId,
            String eventType,
            String signature
    ) {}

    public record LibraryItemDto(
            UUID productId,
            String slug,
            String title,
            String productType,
            String targetGear,
            String sourceGear,
            String creatorName,
            String creatorSlug,
            String demoAudioUrl,
            String purchasedVersion,
            String latestVersion,
            boolean hasUpdate,
            String latestChangelog,
            String unlockedViaBundleTitle,
            Instant purchasedAt,
            List<CatalogDtos.ProductVersionDto> versions
    ) {}

    public record PresignedDownloadDto(
            String downloadUrl,
            String filename,
            Instant expiresAt,
            String storageMode,
            String versionNumber
    ) {}

    public record NotificationDto(
            UUID id,
            NotificationType type,
            String title,
            String message,
            UUID relatedProductId,
            boolean read,
            Instant createdAt
    ) {}
}
