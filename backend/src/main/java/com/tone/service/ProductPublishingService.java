package com.tone.service;

import com.tone.audio.AudioProcessingService;
import com.tone.audio.ProcessedAudio;
import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Product;
import com.tone.domain.entity.ProductVersion;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.dto.CreatorDtos;
import com.tone.dto.PageResponse;
import com.tone.exception.ApiException;
import com.tone.repository.ProductRepository;
import com.tone.repository.ProductVersionRepository;
import com.tone.service.mapper.ProductCardMapper;
import com.tone.storage.FileStorageService;
import com.tone.storage.StoredObject;
import com.tone.upload.UploadPolicy;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class ProductPublishingService {

    private final CreatorProfileService creatorProfileService;
    private final ProductRepository productRepository;
    private final ProductVersionRepository productVersionRepository;
    private final FileStorageService fileStorageService;
    private final AudioProcessingService audioProcessingService;
    private final UploadPolicy uploadPolicy;
    private final ProductCardMapper productCardMapper;

    public ProductPublishingService(
            CreatorProfileService creatorProfileService,
            ProductRepository productRepository,
            ProductVersionRepository productVersionRepository,
            FileStorageService fileStorageService,
            AudioProcessingService audioProcessingService,
            UploadPolicy uploadPolicy,
            ProductCardMapper productCardMapper
    ) {
        this.creatorProfileService = creatorProfileService;
        this.productRepository = productRepository;
        this.productVersionRepository = productVersionRepository;
        this.fileStorageService = fileStorageService;
        this.audioProcessingService = audioProcessingService;
        this.uploadPolicy = uploadPolicy;
        this.productCardMapper = productCardMapper;
    }

    @Transactional
    @CacheEvict(cacheNames = "catalog-filters", allEntries = true)
    public CatalogDtos.ProductCardDto createProduct(
            UUID userId,
            CreatorDtos.CreateProductForm form,
            MultipartFile patchFile,
            MultipartFile demoAudioFile
    ) throws IOException {
        CreatorProfile creator = creatorProfileService.requireApprovedCreator(userId);

        if (form.productType() == ProductType.BUNDLE) {
            throw ApiException.badRequest("Use o endpoint de Pacotes (Bundles) para criar pacotes promocionais.");
        }

        // A3: Validate upload extensions, sizes, and magic bytes before touching audio DSP or storage
        uploadPolicy.validatePatch(patchFile);
        uploadPolicy.validateAudio(demoAudioFile);

        ProcessedAudio processedDemo = audioProcessingService.processDemoAudio(
                demoAudioFile.getBytes(),
                demoAudioFile.getOriginalFilename()
        );
        StoredObject storedDemo = fileStorageService.storePrivateFile(
                "demos",
                "demo." + processedDemo.extension(),
                processedDemo.bytes(),
                processedDemo.contentType()
        );

        StoredObject storedPatch;
        try (InputStream patchStream = patchFile.getInputStream()) {
            storedPatch = fileStorageService.storePrivateFile(
                    "patches",
                    patchFile.getOriginalFilename() != null ? patchFile.getOriginalFilename() : "patch.bin",
                    patchStream,
                    patchFile.getSize(),
                    patchFile.getContentType()
            );
        }

        String versionLabel = (form.version() == null || form.version().isBlank()) ? "1.0.0" : form.version().trim();

        Product product = new Product();
        product.setCreator(creator);
        product.setTitle(form.title().trim());
        product.setSlug(SlugUtils.generateUnique(form.title(), productRepository::existsBySlug));
        product.setDescription(form.description().trim());
        product.setPrice(form.price().setScale(2, RoundingMode.HALF_UP));
        product.setProductType(form.productType());
        product.setGenre(form.genre().trim());
        product.setTargetGear(form.targetGear().trim());
        product.setSourceGear(form.sourceGear().trim());
        product.setPickupType(form.pickupType());
        product.setTags(form.tags() != null ? form.tags().trim() : "");
        product.setVideoUrl(form.videoUrl() != null && !form.videoUrl().isBlank() ? form.videoUrl().trim() : null);
        product.setDemoAudioKey(storedDemo.storageKey());
        product.setDemoDurationSeconds(processedDemo.durationSeconds());
        product.setDemoLoudnessLufs(processedDemo.normalizedLufs());
        product.setDemoBitrateKbps(processedDemo.effectiveBitrateKbps());
        product.setCurrentVersion(versionLabel);
        product.setActive(true);
        product = productRepository.save(product);

        ProductVersion version = new ProductVersion();
        version.setProduct(product);
        version.setVersionNumber(versionLabel);
        version.setChangelog("Lançamento inicial do timbre.");
        version.setPatchFileKey(storedPatch.storageKey());
        version.setOriginalFilename(patchFile.getOriginalFilename() != null ? patchFile.getOriginalFilename() : "patch.bin");
        version.setFileSizeBytes(storedPatch.sizeBytes());
        version.setChecksumSha256(storedPatch.sha256Checksum());
        productVersionRepository.save(version);

        return productCardMapper.toCard(product);
    }

    /**
     * M6: Anti-IDOR listing update — ownership enforced directly in repository query.
     */
    @Transactional
    @CacheEvict(cacheNames = "catalog-filters", allEntries = true)
    public CatalogDtos.ProductCardDto updateProduct(UUID userId, UUID productId, CreatorDtos.UpdateProductRequest req) {
        creatorProfileService.requireApprovedCreator(userId);
        Product product = productRepository.findOwnedBy(productId, userId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        product.updateListing(
                req.title(),
                req.description(),
                req.price(),
                req.genre(),
                req.targetGear(),
                req.sourceGear(),
                req.pickupType(),
                req.tags(),
                req.videoUrl(),
                req.active()
        );
        return productCardMapper.toCard(productRepository.save(product));
    }

    /**
     * M6: Soft-delete listing (existing buyers retain lifetime access in their Digital Library).
     */
    @Transactional
    @CacheEvict(cacheNames = "catalog-filters", allEntries = true)
    public void deactivateProduct(UUID userId, UUID productId) {
        creatorProfileService.requireApprovedCreator(userId);
        Product product = productRepository.findOwnedBy(productId, userId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));
        product.deactivate();
        productRepository.save(product);
    }

    @Transactional(readOnly = true)
    public PageResponse<CatalogDtos.ProductCardDto> getMyProducts(UUID userId, Pageable pageable) {
        CreatorProfile creator = creatorProfileService.requireApprovedCreator(userId);
        Pageable effective = PageRequest.of(
                pageable.getPageNumber(),
                Math.min(pageable.getPageSize(), 100),
                Sort.by(Sort.Direction.DESC, "createdAt")
        );
        Page<Product> page = productRepository.findByCreatorId(creator.getId(), effective);
        List<CatalogDtos.ProductCardDto> cards = productCardMapper.toCards(page.getContent());
        return PageResponse.of(cards, page);
    }
}
