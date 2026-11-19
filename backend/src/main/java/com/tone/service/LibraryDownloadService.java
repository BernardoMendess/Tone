package com.tone.service;

import com.tone.domain.entity.*;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.dto.OrderDtos;
import com.tone.exception.ApiException;
import com.tone.repository.*;
import com.tone.service.mapper.ProductVersionMapper;
import com.tone.storage.FileStorageService;
import com.tone.storage.PresignedDownload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * RF04 + RF09 + RNF02 + A4 (batch loading of bundles and versions — zero N+1).
 */
@Service
public class LibraryDownloadService {

    private final OrderItemRepository orderItemRepository;
    private final BundleItemRepository bundleItemRepository;
    private final ProductVersionRepository versionRepository;
    private final ProductRepository productRepository;
    private final FileStorageService fileStorageService;
    private final long presignedTtlSeconds;

    public LibraryDownloadService(
            OrderItemRepository orderItemRepository,
            BundleItemRepository bundleItemRepository,
            ProductVersionRepository versionRepository,
            ProductRepository productRepository,
            FileStorageService fileStorageService,
            @Value("${app.storage.presigned-ttl-seconds:300}") long presignedTtlSeconds
    ) {
        this.orderItemRepository = orderItemRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.versionRepository = versionRepository;
        this.productRepository = productRepository;
        this.fileStorageService = fileStorageService;
        this.presignedTtlSeconds = presignedTtlSeconds;
    }

    private record EntrySeed(Product product, String purchasedVersion, String viaBundleTitle, Instant purchasedAt) {}

    @Transactional(readOnly = true)
    public List<OrderDtos.LibraryItemDto> getDigitalLibrary(UUID userId) {
        List<OrderItem> purchasedItems = orderItemRepository.findPurchasedItemsByBuyer(userId, OrderStatus.PAID);
        if (purchasedItems.isEmpty()) {
            return List.of();
        }

        // Batch-load all bundle items in a single query
        List<UUID> bundleIds = purchasedItems.stream()
                .map(OrderItem::getProduct)
                .filter(p -> p.getProductType() == ProductType.BUNDLE)
                .map(Product::getId)
                .distinct()
                .toList();

        Map<UUID, List<BundleItem>> bundleItemsByBundle = bundleIds.isEmpty()
                ? Map.of()
                : bundleItemRepository.findByBundleIdInWithProducts(bundleIds).stream()
                        .collect(Collectors.groupingBy(bi -> bi.getBundle().getId()));

        Map<UUID, EntrySeed> seedsByProductId = new LinkedHashMap<>();
        for (OrderItem oi : purchasedItems) {
            Product product = oi.getProduct();
            if (product.getProductType() == ProductType.BUNDLE) {
                for (BundleItem bi : bundleItemsByBundle.getOrDefault(product.getId(), List.of())) {
                    Product included = bi.getIncludedProduct();
                    seedsByProductId.putIfAbsent(
                            included.getId(),
                            new EntrySeed(included, oi.getPurchasedVersion(), product.getTitle(), oi.getOrder().getPaidAt())
                    );
                }
            } else {
                seedsByProductId.putIfAbsent(
                        product.getId(),
                        new EntrySeed(product, oi.getPurchasedVersion(), null, oi.getOrder().getPaidAt())
                );
            }
        }

        if (seedsByProductId.isEmpty()) {
            return List.of();
        }

        // Batch-load all product versions in a single query
        Map<UUID, List<ProductVersion>> versionsByProduct = versionRepository
                .findByProductIdInOrderByCreatedAtDesc(seedsByProductId.keySet())
                .stream()
                .collect(Collectors.groupingBy(v -> v.getProduct().getId()));

        return seedsByProductId.values().stream()
                .map(seed -> buildLibraryEntry(seed, versionsByProduct.getOrDefault(seed.product().getId(), List.of())))
                .toList();
    }

    @Transactional(readOnly = true)
    public OrderDtos.PresignedDownloadDto generatePresignedDownload(UUID userId, UUID productId, UUID versionId) {
        Product product = productRepository.findByIdWithCreator(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        boolean isOwnerCreator = product.isOwnedBy(userId);
        boolean isVerifiedBuyer = orderItemRepository.hasVerifiedPurchase(userId, productId, OrderStatus.PAID);

        if (!isVerifiedBuyer && !isOwnerCreator) {
            throw ApiException.forbidden("Download bloqueado: compra verificada necessária para gerar URL temporária.");
        }

        ProductVersion version = (versionId != null)
                ? versionRepository.findByIdAndProductId(versionId, productId)
                        .orElseThrow(() -> ApiException.notFound("Versão solicitada não encontrada."))
                : versionRepository.findFirstByProductIdOrderByCreatedAtDesc(productId)
                        .orElseThrow(() -> ApiException.notFound("Nenhum arquivo binário disponível para este timbre."));

        PresignedDownload presigned = fileStorageService.generatePresignedDownloadUrl(
                version.getPatchFileKey(),
                version.getOriginalFilename(),
                userId,
                Duration.ofSeconds(presignedTtlSeconds),
                false
        );

        return new OrderDtos.PresignedDownloadDto(
                presigned.url(),
                presigned.filename(),
                presigned.expiresAt(),
                presigned.storageMode(),
                version.getVersionNumber()
        );
    }

    private OrderDtos.LibraryItemDto buildLibraryEntry(EntrySeed seed, List<ProductVersion> versions) {
        Product product = seed.product();
        List<CatalogDtos.ProductVersionDto> versionDtos = versions.stream()
                .map(ProductVersionMapper::toDto)
                .toList();

        String latestVersion = product.getCurrentVersion();
        String latestChangelog = versions.isEmpty() ? null : versions.getFirst().getChangelog();
        boolean hasUpdate = seed.purchasedVersion() != null && !seed.purchasedVersion().equals(latestVersion);

        return new OrderDtos.LibraryItemDto(
                product.getId(),
                product.getSlug(),
                product.getTitle(),
                product.getProductType().name(),
                product.getTargetGear(),
                product.getSourceGear(),
                product.getCreator().getDisplayName(),
                product.getCreator().getSlug(),
                product.getDemoAudioKey() != null ? "/api/audio/demo/" + product.getId() : null,
                seed.purchasedVersion(),
                latestVersion,
                hasUpdate,
                latestChangelog,
                seed.viaBundleTitle(),
                seed.purchasedAt(),
                versionDtos
        );
    }
}
