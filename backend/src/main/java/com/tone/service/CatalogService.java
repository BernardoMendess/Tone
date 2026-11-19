package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Product;
import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.PickupType;
import com.tone.domain.enums.ProductType;
import com.tone.domain.enums.UserRole;
import com.tone.dto.CatalogDtos;
import com.tone.dto.PageResponse;
import com.tone.exception.ApiException;
import com.tone.repository.*;
import com.tone.service.mapper.ProductCardMapper;
import com.tone.service.mapper.ProductVersionMapper;
import jakarta.persistence.criteria.Join;
import jakarta.persistence.criteria.JoinType;
import jakarta.persistence.criteria.Predicate;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
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
    private final ProductCardMapper productCardMapper;

    public CatalogService(
            ProductRepository productRepository,
            ProductVersionRepository versionRepository,
            BundleItemRepository bundleItemRepository,
            ReviewRepository reviewRepository,
            CreatorProfileRepository creatorProfileRepository,
            OrderItemRepository orderItemRepository,
            WishlistItemRepository wishlistItemRepository,
            ProductCardMapper productCardMapper
    ) {
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.reviewRepository = reviewRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.orderItemRepository = orderItemRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.productCardMapper = productCardMapper;
    }

    /**
     * A4 + B3: Paginated cross-filtered catalog search with escaped LIKE wildcards,
     * JOIN FETCH on non-count query, and batch bundle aggregation (zero N+1).
     */
    public PageResponse<CatalogDtos.ProductCardDto> searchProducts(
            String query,
            String genre,
            String targetGear,
            String sourceGear,
            PickupType pickupType,
            ProductType productType,
            BigDecimal minPrice,
            BigDecimal maxPrice,
            String sortBy,
            Pageable pageable
    ) {
        Specification<Product> spec = (root, cq, cb) -> {
            Join<Object, Object> creatorJoin;
            boolean isCountQuery = cq != null
                    && (cq.getResultType() == Long.class || cq.getResultType() == long.class);
            if (!isCountQuery) {
                @SuppressWarnings("unchecked")
                Join<Object, Object> fetched = (Join<Object, Object>) root.fetch("creator", JoinType.INNER);
                creatorJoin = fetched;
            } else {
                creatorJoin = root.join("creator", JoinType.INNER);
            }

            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.isTrue(root.get("active")));
            predicates.add(cb.equal(creatorJoin.get("status"), CreatorStatus.APPROVED));

            if (query != null && !query.isBlank()) {
                String pattern = "%" + escapeLike(query.trim().toLowerCase(Locale.ROOT)) + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("title")), pattern, '\\'),
                        cb.like(cb.lower(root.get("description")), pattern, '\\'),
                        cb.like(cb.lower(root.get("tags")), pattern, '\\'),
                        cb.like(cb.lower(root.get("targetGear")), pattern, '\\'),
                        cb.like(cb.lower(root.get("sourceGear")), pattern, '\\'),
                        cb.like(cb.lower(root.get("genre")), pattern, '\\'),
                        cb.like(cb.lower(creatorJoin.get("displayName")), pattern, '\\')
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

        int safeSize = Math.min(Math.max(pageable.getPageSize(), 1), 100);
        Pageable effective = PageRequest.of(Math.max(pageable.getPageNumber(), 0), safeSize, sort);
        Page<Product> page = productRepository.findAll(spec, effective);
        List<CatalogDtos.ProductCardDto> cards = productCardMapper.toCards(page.getContent());
        return PageResponse.of(cards, page);
    }

    @Cacheable(cacheNames = "catalog-filters", key = "'all'")
    public CatalogDtos.FilterOptionsDto getFilterOptions() {
        return new CatalogDtos.FilterOptionsDto(
                productRepository.findDistinctGenres(CreatorStatus.APPROVED),
                productRepository.findDistinctTargetGears(CreatorStatus.APPROVED),
                productRepository.findDistinctSourceGears(CreatorStatus.APPROVED),
                Arrays.asList(PickupType.values()),
                Arrays.asList(ProductType.values())
        );
    }

    public CatalogDtos.ProductDetailDto getProductDetail(String slugOrId, UUID currentUserId, UserRole currentRole) {
        Product product = resolveVisibleProduct(slugOrId, currentUserId, currentRole);

        List<CatalogDtos.ProductVersionDto> versions = versionRepository
                .findByProductIdOrderByCreatedAtDesc(product.getId())
                .stream()
                .map(ProductVersionMapper::toDto)
                .toList();

        List<CatalogDtos.ProductCardDto> bundleItems = product.getProductType() == ProductType.BUNDLE
                ? productCardMapper.toCards(
                        bundleItemRepository.findByBundleIdWithProducts(product.getId()).stream()
                                .map(bi -> bi.getIncludedProduct())
                                .toList()
                )
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

        boolean owned = currentUserId != null
                && orderItemRepository.hasVerifiedPurchase(currentUserId, product.getId(), OrderStatus.PAID);
        boolean wishlisted = currentUserId != null
                && wishlistItemRepository.existsByUserIdAndProductId(currentUserId, product.getId());
        boolean canReview = owned && !product.isOwnedBy(currentUserId);

        return new CatalogDtos.ProductDetailDto(
                productCardMapper.toCard(product),
                product.getVideoUrl(),
                versions,
                bundleItems,
                reviews,
                owned,
                wishlisted,
                canReview
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
                productCardMapper.toCards(products)
        );
    }

    private Product resolveVisibleProduct(String slugOrId, UUID currentUserId, UserRole currentRole) {
        Product product = resolveAnyProduct(slugOrId);
        if (product.isPurchasable()) {
            return product;
        }
        boolean isAdmin = currentRole == UserRole.ADMIN;
        boolean isOwner = product.isOwnedBy(currentUserId);
        boolean isBuyer = currentUserId != null
                && orderItemRepository.hasVerifiedPurchase(currentUserId, product.getId(), OrderStatus.PAID);
        if (isAdmin || isOwner || isBuyer) {
            return product;
        }
        throw ApiException.notFound("Timbre não encontrado.");
    }

    private Product resolveAnyProduct(String slugOrId) {
        try {
            UUID id = UUID.fromString(slugOrId);
            return productRepository.findByIdWithCreator(id)
                    .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        } catch (IllegalArgumentException ignored) {
            return productRepository.findBySlugWithCreator(slugOrId)
                    .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        }
    }

    private static String escapeLike(String input) {
        return input.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
