package com.toneguitar.service;

import com.toneguitar.domain.entity.BundleItem;
import com.toneguitar.domain.entity.CreatorProfile;
import com.toneguitar.domain.entity.Product;
import com.toneguitar.domain.enums.CreatorStatus;
import com.toneguitar.domain.enums.OrderStatus;
import com.toneguitar.domain.enums.PickupType;
import com.toneguitar.domain.enums.ProductType;
import com.toneguitar.dto.CatalogDtos;
import com.toneguitar.exception.ApiException;
import com.toneguitar.repository.*;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

@Service
@Transactional(readOnly = true)
public class CatalogService {

    private final ProductRepository productRepository;
    private final ProductVersionRepository versionRepository;
    private final BundleItemRepository bundleItemRepository;
    private final ReviewRepository reviewRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final OrderItemRepository orderItemRepository;
    private final WishlistItemRepository wishlistItemRepository;

    public CatalogService(
            ProductRepository productRepository,
            ProductVersionRepository versionRepository,
            BundleItemRepository bundleItemRepository,
            ReviewRepository reviewRepository,
            CreatorProfileRepository creatorProfileRepository,
            OrderItemRepository orderItemRepository,
            WishlistItemRepository wishlistItemRepository
    ) {
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.reviewRepository = reviewRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.orderItemRepository = orderItemRepository;
        this.wishlistItemRepository = wishlistItemRepository;
    }

    /**
     * RF01 - Pesquisa e Filtragem cruzada por gênero musical, equipamento de destino,
     * equipamento de origem, tipo de captação, tipo de ativo e busca textual.
     */
    public List<CatalogDtos.ProductCardDto> searchProducts(
            String query,
            String genre,
            String targetGear,
            String sourceGear,
            PickupType pickupType,
            ProductType productType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy
    ) {
        Specification<Product> spec = (root, cq, cb) -> {
            var creatorJoin = root.join("creator", JoinType.INNER);
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.equal(creatorJoin.get("status"), CreatorStatus.APPROVED));

            if (query != null && !query.isBlank()) {
                String pattern = "%" + query.trim().toLowerCase(Locale.ROOT) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern),
                        cb.like(cb.lower(root.get("description")), pattern),
                        cb.like(cb.lower(root.get("tags")), pattern),
                        cb.like(cb.lower(root.get("targetGear")), pattern),
                        cb.like(cb.lower(root.get("sourceGear")), pattern),
                        cb.like(cb.lower(root.get("genre")), pattern),
                        cb.like(cb.lower(creatorJoin.get("displayName")), pattern)
                ));
            }
            if (genre != null && !genre.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("genre")), genre.trim().toLowerCase(Locale.ROOT)));
            }
            if (targetGear != null && !targetGear.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("targetGear")), targetGear.trim().toLowerCase(Locale.ROOT)));
            }
            if (sourceGear != null && !sourceGear.isBlank()) {
                predicates.add(cb.equal(cb.lower(root.get("sourceGear")), sourceGear.trim().toLowerCase(Locale.ROOT)));
            }
            if (pickupType != null) {
                predicates.add(cb.or(
                        cb.equal(root.get("pickupType"), pickupType),
                        cb.equal(root.get("pickupType"), PickupType.ANY)
                ));
            }
            if (productType != null) {
                predicates.add(cb.equal(root.get("productType"), productType));
            }
            if (minPrice != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("price"), minPrice));
            }
            if (maxPrice != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("price"), maxPrice));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Sort sort = switch (sortBy != null ? sortBy : "popular") {
            case "price_asc" -> Sort.by(Sort.Direction.ASC, "price");
            case "price_desc" -> Sort.by(Sort.Direction.DESC, "price");
            case "rating" -> Sort.by(Sort.Direction.DESC, "averageRating", "reviewCount");
            case "newest" -> Sort.by(Sort.Direction.DESC, "createdAt");
            default -> Sort.by(Sort.Direction.DESC, "salesCount", "createdAt");
        };

        List<Product> products = productRepository.findAll(spec, sort);
        return products.stream().map(this::toProductCard).toList();
    }

    public CatalogDtos.FilterOptionsDto getFilterOptions() {
        return new CatalogDtos.FilterOptionsDto(
                productRepository.findDistinctGenres(CreatorStatus.APPROVED),
                productRepository.findDistinctTargetGears(CreatorStatus.APPROVED),
                productRepository.findDistinctSourceGears(CreatorStatus.APPROVED),
                Arrays.asList(PickupType.values()),
                Arrays.asList(ProductType.values())
        );
    }

    public CatalogDtos.ProductDetailDto getProductDetail(String slugOrId, UUID currentUserId) {
        Product product = resolveProduct(slugOrId);
        List<CatalogDtos.ProductVersionDto> versions = versionRepository
                .findByProductIdOrderByCreatedAtDesc(product.getId())
                .stream()
                .map(v -> new CatalogDtos.ProductVersionDto(
                        v.getId(),
                        v.getVersionNumber(),
                        v.getChangelog(),
                        v.getOriginalFilename(),
                        v.getFileSizeBytes(),
                        v.getCreatedAt()
                ))
                .toList();

        List<CatalogDtos.ProductCardDto> bundleItems = product.getProductType() == ProductType.BUNDLE
                ? bundleItemRepository.findByBundleIdWithProducts(product.getId()).stream()
                        .map(bi -> toProductCard(bi.getIncludedProduct()))
                        .toList()
                : List.of();

        List<CatalogDtos.ReviewDto> reviews = reviewRepository.findByProductIdWithUser(product.getId()).stream()
                .map(r -> new CatalogDtos.ReviewDto(
                        r.getId(),
                        r.getUser().getId(),
                        r.getUser().getFullName(),
                        r.getRating(),
                        r.getComment(),
                        r.isVerifiedPurchase(),
                        r.getCreatedAt(),
                        r.getUpdatedAt()
                ))
                .toList();

        boolean owned = currentUserId != null &&
                orderItemRepository.hasVerifiedPurchase(currentUserId, product.getId(), OrderStatus.PAID);
        boolean wishlisted = currentUserId != null &&
                wishlistItemRepository.existsByUserIdAndProductId(currentUserId, product.getId());

        return new CatalogDtos.ProductDetailDto(
                toProductCard(product),
                product.getVideoUrl(),
                versions,
                bundleItems,
                reviews,
                owned,
                wishlisted,
                owned
        );
    }

    public CatalogDtos.PublicCreatorProfileDto getPublicCreatorProfile(String slug) {
        CreatorProfile creator = creatorProfileRepository.findBySlugWithUser(slug)
                .orElseThrow(() -> ApiException.notFound("Criador não encontrado."));
        if (creator.getStatus() != CreatorStatus.APPROVED) {
            throw ApiException.notFound("Perfil público deste criador ainda não está disponível.");
        }
        List<Product> products = productRepository.findActiveByCreatorId(creator.getId());
        int totalSales = products.stream().mapToInt(Product::getSalesCount).sum();
        List<Product> rated = products.stream().filter(p -> p.getReviewCount() > 0).toList();
        BigDecimal avgRating = rated.isEmpty()
                ? BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
                : rated.stream()
                        .map(Product::getAverageRating)
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .divide(BigDecimal.valueOf(rated.size()), 2, RoundingMode.HALF_UP);

        return new CatalogDtos.PublicCreatorProfileDto(
                creator.getId(),
                creator.getDisplayName(),
                creator.getSlug(),
                creator.getBio(),
                creator.getAvatarUrl(),
                creator.getMainGear(),
                creator.getYoutubeUrl(),
                creator.getInstagramUrl(),
                creator.getWebsiteUrl(),
                products.size(),
                totalSales,
                avgRating,
                products.stream().map(this::toProductCard).toList()
        );
    }

    public Product resolveProduct(String slugOrId) {
        try {
            UUID id = UUID.fromString(slugOrId);
            return productRepository.findByIdWithCreator(id)
                    .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        } catch (IllegalArgumentException ignored) {
            return productRepository.findBySlugWithCreator(slugOrId)
                    .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        }
    }

    public CatalogDtos.ProductCardDto toProductCard(Product p) {
        BigDecimal originalBundlePrice = null;
        Integer discountPercent = null;
        int includedCount = 0;

        if (p.getProductType() == ProductType.BUNDLE) {
            List<BundleItem> items = bundleItemRepository.findByBundleIdWithProducts(p.getId());
            includedCount = items.size();
            if (!items.isEmpty()) {
                originalBundlePrice = items.stream()
                        .map(bi -> bi.getIncludedProduct().getPrice())
                        .reduce(BigDecimal.ZERO, BigDecimal::add)
                        .setScale(2, RoundingMode.HALF_UP);
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
                creatorSummary,
                p.getCreatedAt()
        );
    }
}
