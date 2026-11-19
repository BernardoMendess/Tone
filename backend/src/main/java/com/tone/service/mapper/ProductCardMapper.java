package com.tone.service.mapper;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Product;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.repository.BundleItemRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class ProductCardMapper {

    private final BundleItemRepository bundleItemRepository;

    public ProductCardMapper(BundleItemRepository bundleItemRepository) {
        this.bundleItemRepository = bundleItemRepository;
    }

    /**
     * A4: Batch-maps products with a single aggregate query for all bundles (zero N+1).
     */
    public List<CatalogDtos.ProductCardDto> toCards(Collection<Product> products) {
        if (products.isEmpty()) {
            return List.of();
        }
        List<UUID> bundleIds = products.stream()
                .filter(p -> p.getProductType() == ProductType.BUNDLE)
                .map(Product::getId)
                .toList();

        Map<UUID, BundleItemRepository.BundleAggregate> aggregates = bundleIds.isEmpty()
                ? Map.of()
                : bundleItemRepository.aggregateByBundleIds(bundleIds).stream()
                        .collect(Collectors.toMap(
                                BundleItemRepository.BundleAggregate::getBundleId,
                                Function.identity()
                        ));

        return products.stream()
                .map(p -> toCard(p, aggregates.get(p.getId())))
                .toList();
    }

    public CatalogDtos.ProductCardDto toCard(Product p) {
        BundleItemRepository.BundleAggregate agg = null;
        if (p.getProductType() == ProductType.BUNDLE) {
            agg = bundleItemRepository.aggregateByBundleIds(List.of(p.getId())).stream().findFirst().orElse(null);
        }
        return toCard(p, agg);
    }

    public CatalogDtos.ProductCardDto toCard(Product p, BundleItemRepository.BundleAggregate agg) {
        BigDecimal originalBundlePrice = null;
        Integer discountPercent = null;
        int includedCount = 0;

        if (p.getProductType() == ProductType.BUNDLE && agg != null && agg.getItemCount() > 0) {
            includedCount = (int) agg.getItemCount();
            if (agg.getOriginalPrice() != null) {
                originalBundlePrice = agg.getOriginalPrice().setScale(2, RoundingMode.HALF_UP);
                if (originalBundlePrice.compareTo(p.getPrice()) > 0) {
                    BigDecimal diff = originalBundlePrice.subtract(p.getPrice());
                    discountPercent = diff.multiply(BigDecimal.valueOf(100))
                            .divide(originalBundlePrice, 0, RoundingMode.HALF_UP)
                            .intValue();
                }
            }
        }

        List<String> tagList = (p.getTags() == null || p.getTags().isBlank())
                ? List.of()
                : Arrays.stream(p.getTags().split(","))
                        .map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .toList();

        CreatorProfile c = p.getCreator();
        CatalogDtos.CreatorSummary creatorSummary = new CatalogDtos.CreatorSummary(
                c.getId(),
                c.getDisplayName(),
                c.getSlug(),
                c.getAvatarUrl(),
                c.getMainGear()
        );

        String demoUrl = (p.getDemoAudioKey() != null && !p.getDemoAudioKey().isBlank())
                ? "/api/audio/demo/" + p.getId()
                : null;

        return new CatalogDtos.ProductCardDto(
                p.getId(),
                p.getTitle(),
                p.getSlug(),
                p.getDescription(),
                p.getPrice(),
                originalBundlePrice,
                discountPercent,
                p.getProductType(),
                p.getGenre(),
                p.getTargetGear(),
                p.getSourceGear(),
                p.getPickupType(),
                tagList,
                demoUrl,
                p.getDemoDurationSeconds(),
                p.getDemoLoudnessLufs(),
                p.getDemoBitrateKbps(),
                p.getCurrentVersion(),
                p.getAverageRating(),
                p.getReviewCount(),
                p.getSalesCount(),
                includedCount,
                p.isActive(),
                creatorSummary,
                p.getCreatedAt()
        );
    }
}
