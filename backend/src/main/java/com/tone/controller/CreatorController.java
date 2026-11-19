package com.tone.controller;

import com.tone.dto.CatalogDtos;
import com.tone.dto.CreatorDtos;
import com.tone.dto.PageResponse;
import com.tone.security.UserPrincipal;
import com.tone.service.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator")
@Validated
public class CreatorController {

    private final CreatorProfileService profileService;
    private final ProductPublishingService productPublishingService;
    private final BundleService bundleService;
    private final ProductVersioningService versioningService;
    private final CreatorDashboardService dashboardService;
    private final PayoutService payoutService;

    public CreatorController(
            CreatorProfileService profileService,
            ProductPublishingService productPublishingService,
            BundleService bundleService,
            ProductVersioningService versioningService,
            CreatorDashboardService dashboardService,
            PayoutService payoutService
    ) {
        this.profileService = profileService;
        this.productPublishingService = productPublishingService;
        this.bundleService = bundleService;
        this.versioningService = versioningService;
        this.dashboardService = dashboardService;
        this.payoutService = payoutService;
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorDtos.CreatorProfileDto apply(
            @Valid @RequestBody CreatorDtos.CreatorApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return profileService.applyAsCreator(principal.getId(), request);
    }

    @GetMapping("/profile")
    public CreatorDtos.CreatorProfileDto getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return profileService.getMyCreatorProfile(principal.getId());
    }

    @PutMapping("/profile")
    public CreatorDtos.CreatorProfileDto updateProfile(
            @Valid @RequestBody CreatorDtos.UpdateCreatorProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return profileService.updateMyProfile(principal.getId(), request);
    }

    @GetMapping("/products")
    public PageResponse<CatalogDtos.ProductCardDto> myProducts(
            @PageableDefault(size = 24) Pageable pageable,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return productPublishingService.getMyProducts(principal.getId(), pageable);
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductCardDto createProduct(
            @Valid @ModelAttribute CreatorDtos.CreateProductForm form,
            @RequestPart("patchFile") MultipartFile patchFile,
            @RequestPart("demoAudioFile") MultipartFile demoAudioFile,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        return productPublishingService.createProduct(principal.getId(), form, patchFile, demoAudioFile);
    }

    @PatchMapping("/products/{productId}")
    public CatalogDtos.ProductCardDto updateProduct(
            @PathVariable UUID productId,
            @Valid @RequestBody CreatorDtos.UpdateProductRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return productPublishingService.updateProduct(principal.getId(), productId, request);
    }

    @DeleteMapping("/products/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deactivateProduct(
            @PathVariable UUID productId,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        productPublishingService.deactivateProduct(principal.getId(), productId);
    }

    @PostMapping("/bundles")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductCardDto createBundle(
            @Valid @RequestBody CreatorDtos.CreateBundleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return bundleService.createBundle(principal.getId(), request);
    }

    @PostMapping(value = "/products/{productId}/versions", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductVersionDto publishVersion(
            @PathVariable UUID productId,
            @RequestParam String versionNumber,
            @RequestParam(required = false) String changelog,
            @RequestPart("patchFile") MultipartFile patchFile,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        return versioningService.publishNewVersion(principal.getId(), productId, versionNumber, changelog, patchFile);
    }

    @GetMapping("/dashboard")
    public CreatorDtos.CreatorDashboardDto getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return dashboardService.getFinancialDashboard(principal.getId());
    }

    @PostMapping("/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorDtos.PayoutDto requestPayout(
            @RequestHeader("Idempotency-Key") @NotBlank @Size(min = 8, max = 80) String idempotencyKey,
            @Valid @RequestBody CreatorDtos.RequestPayoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return payoutService.requestPayout(principal.getId(), idempotencyKey, request);
    }
}
