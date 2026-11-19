package com.toneguitar.controller;

import com.toneguitar.domain.enums.PickupType;
import com.toneguitar.domain.enums.ProductType;
import com.toneguitar.dto.CatalogDtos;
import com.toneguitar.dto.CreatorDtos;
import com.toneguitar.security.UserPrincipal;
import com.toneguitar.service.CreatorStudioService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/creator")
public class CreatorController {

    private final CreatorStudioService creatorStudioService;

    public CreatorController(CreatorStudioService creatorStudioService) {
        this.creatorStudioService = creatorStudioService;
    }

    @PostMapping("/apply")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorDtos.CreatorProfileDto apply(
            @Valid @RequestBody CreatorDtos.CreatorApplicationRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return creatorStudioService.applyAsCreator(principal.getId(), request);
    }

    @GetMapping("/profile")
    public CreatorDtos.CreatorProfileDto getMyProfile(@AuthenticationPrincipal UserPrincipal principal) {
        return creatorStudioService.getMyCreatorProfile(principal.getId());
    }

    @PutMapping("/profile")
    public CreatorDtos.CreatorProfileDto updateProfile(
            @Valid @RequestBody CreatorDtos.UpdateCreatorProfileRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return creatorStudioService.updateMyProfile(principal.getId(), request);
    }

    @GetMapping("/products")
    public List<CatalogDtos.ProductCardDto> myProducts(@AuthenticationPrincipal UserPrincipal principal) {
        return creatorStudioService.getMyProducts(principal.getId());
    }

    @PostMapping(value = "/products", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductCardDto createProduct(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam BigDecimal price,
            @RequestParam ProductType productType,
            @RequestParam String genre,
            @RequestParam String targetGear,
            @RequestParam String sourceGear,
            @RequestParam PickupType pickupType,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String videoUrl,
            @RequestParam(required = false, defaultValue = "1.0.0") String version,
            @RequestPart("patchFile") MultipartFile patchFile,
            @RequestPart("demoAudioFile") MultipartFile demoAudioFile,
            @AuthenticationPrincipal UserPrincipal principal
    ) throws IOException {
        return creatorStudioService.createProduct(
                principal.getId(), title, description, price, productType, genre,
                targetGear, sourceGear, pickupType, tags, videoUrl, version, patchFile, demoAudioFile
        );
    }

    @PostMapping("/bundles")
    @ResponseStatus(HttpStatus.CREATED)
    public CatalogDtos.ProductCardDto createBundle(
            @Valid @RequestBody CreatorDtos.CreateBundleRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return creatorStudioService.createBundle(principal.getId(), request);
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
        return creatorStudioService.publishNewVersion(principal.getId(), productId, versionNumber, changelog, patchFile);
    }

    @GetMapping("/dashboard")
    public CreatorDtos.CreatorDashboardDto getDashboard(@AuthenticationPrincipal UserPrincipal principal) {
        return creatorStudioService.getFinancialDashboard(principal.getId());
    }

    @PostMapping("/payouts")
    @ResponseStatus(HttpStatus.CREATED)
    public CreatorDtos.PayoutDto requestPayout(
            @Valid @RequestBody CreatorDtos.RequestPayoutRequest request,
            @AuthenticationPrincipal UserPrincipal principal
    ) {
        return creatorStudioService.requestPayout(principal.getId(), request);
    }
}
