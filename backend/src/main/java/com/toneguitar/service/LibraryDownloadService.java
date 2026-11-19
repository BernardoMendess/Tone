package com.toneguitar.service;

import com.toneguitar.domain.entity.*;
import com.toneguitar.domain.enums.OrderStatus;
import com.toneguitar.domain.enums.ProductType;
import com.toneguitar.dto.CatalogDtos;
import com.toneguitar.dto.OrderDtos;
import com.toneguitar.exception.ApiException;
import com.toneguitar.repository.*;
import com.toneguitar.storage.FileStorageService;
import com.toneguitar.storage.PresignedDownload;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.*;

/**
 * RF04 (Biblioteca Digital vitalícia) + RF09 (acesso a versões atualizadas) +
 * RNF02 (geração de Presigned URLs temporárias após verificação de compra).
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

    @Transactional(readOnly = true)
    public List<OrderDtos.LibraryItemDto> getDigitalLibrary(UUID userId) {
        List<OrderItem> purchasedItems = orderItemRepository.findPurchasedItemsByBuyer(userId, OrderStatus.PAID);
        Map<UUID, OrderDtos.LibraryItemDto> libraryByProductId = new LinkedHashMap<>();

        for (OrderItem oi : purchasedItems) {
            Product product = oi.getProduct();
            if (product.getProductType() == ProductType.BUNDLE) {
                // RF08: Bundles unlock all included patches individually in the Digital Library
                List<BundleItem> bundleItems = bundleItemRepository.findByBundleIdWithProducts(product.getId());
                for (BundleItem bi : bundleItems) {
                    Product included = bi.getIncludedProduct();
                    libraryByProductId.putIfAbsent(
                            included.getId(),
                            buildLibraryEntry(included, oi.getPurchasedVersion(), product.getTitle(), oi.getOrder().getPaidAt())
                    );
                }
            } else {
                libraryByProductId.putIfAbsent(
                        product.getId(),
                        buildLibraryEntry(product, oi.getPurchasedVersion(), null, oi.getOrder().getPaidAt())
                );
            }
        }
        return new ArrayList<>(libraryByProductId.values());
    }

    @Transactional(readOnly = true)
    public OrderDtos.PresignedDownloadDto generatePresignedDownload(UUID userId, UUID productId, UUID versionId) {
        Product product = productRepository.findByIdWithCreator(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        // Allow verified buyers OR the product's own creator to generate a presigned download
        boolean isOwnerCreator = product.getCreator().getUser().getId().equals(userId);
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
                Duration.ofSeconds(presignedTtlSeconds)
        );

        return new OrderDtos.PresignedDownloadDto(
                presigned.url(),
                presigned.filename(),
                presigned.expiresAt(),
                presigned.storageMode(),
                version.getVersionNumber()
        );
    }

    private OrderDtos.LibraryItemDto buildLibraryEntry(
            Product product,
            String purchasedVersion,
            String unlockedViaBundleTitle,
            java.time.Instant purchasedAt
    ) {
        List<ProductVersion> versions = versionRepository.findByProductIdOrderByCreatedAtDesc(product.getId());
        List<CatalogDtos.ProductVersionDto> versionDtos = versions.stream()
                .map(v -> new CatalogDtos.ProductVersionDto(
                        v.getId(),
                        v.getVersionNumber(),
                        v.getChangelog(),
                        v.getOriginalFilename(),
                        v.getFileSizeBytes(),
                        v.getCreatedAt()
                ))
                .toList();

        String latestVersion = product.getCurrentVersion();
        String latestChangelog = versions.isEmpty() ? null : versions.getFirst().getChangelog();
        boolean hasUpdate = purchasedVersion != null && !purchasedVersion.equals(latestVersion);

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
                purchasedVersion,
                latestVersion,
                hasUpdate,
                latestChangelog,
                unlockedViaBundleTitle,
                purchasedAt,
                versionDtos
        );
    }
}
