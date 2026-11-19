package com.tone.controller;

import com.tone.domain.enums.PickupType;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.security.UserPrincipal;
import com.tone.service.CatalogService;
import com.tone.service.ReviewService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api")
public class CatalogController {

    private final CatalogService catalogService;
    private final ReviewService reviewService;

    public CatalogController(CatalogService catalogService, ReviewService reviewService) {
        this.catalogService = catalogService;
        this.reviewService = reviewService;
    }

    @GetMapping("/products")
    public List<CatalogDtos.ProductCardDto> searchProducts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String genre,
            @RequestParam(required = false) String targetGear,
            @RequestParam(required = false) String sourceGear,
            @RequestParam(required = false) PickupType pickupType,
            @RequestParam(required = false) ProductType productType,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(required = false, defaultValue = "popular") String sort
    ) {
        return catalogService.searchProducts(q, genre, targetGear, sourceGear, pickupType, productType, minPrice, maxPrice, sort);
    }

    @GetMapping("/products/filters")
    public CatalogDtos.FilterOptionsDto getFilters() {
        return catalogService.getFilterOptions();
    }

    @GetMapping("/products/{slugOrId}")
    public CatalogDtos.ProductDetailDto getProductDetail(
            @PathVariable String slugOrId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        UUID userId = principal != null ? principal.getId() : null;
        return catalogService.getProductDetail(slugOrId, userId);
    }

    @GetMapping("/products/{productId}/reviews")
    public List<CatalogDtos.ReviewDto> getReviews(@PathVariable UUID productId) {
        return reviewService.listReviews(productId);
    }

    @PostMapping("/products/{productId}/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ReviewDto submitReview(
            @PathVariable UUID productId,
            @Valid @RequestBody CatalogDtos.CreateReviewRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return reviewService.submitReview(principal.getId(), productId, request);
    }

    @GetMapping("/creators/{slug}")
    public CatalogDtos.PublicCreatorProfileDto getPublicCreator(@PathVariable String slug) {
        return catalogService.getPublicCreatorProfile(slug);
    }
}
