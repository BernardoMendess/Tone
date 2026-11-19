package com.tone.service;

import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Product;
import com.tone.domain.entity.ProductVersion;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.exception.ApiException;
import com.tone.repository.ProductRepository;
import com.tone.repository.ProductVersionRepository;
import com.tone.service.event.PatchVersionPublishedEvent;
import com.tone.service.mapper.ProductVersionMapper;
import com.tone.storage.FileStorageService;
import com.tone.storage.StorageSupport;
import com.tone.storage.StoredObject;
import com.tone.upload.UploadPolicy;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.UUID;

@Service
public class ProductVersioningService {

    private final CreatorProfileService creatorProfileService;
    private final ProductRepository productRepository;
    private final ProductVersionRepository productVersionRepository;
    private final FileStorageService fileStorageService;
    private final UploadPolicy uploadPolicy;
    private final ApplicationEventPublisher eventPublisher;

    public ProductVersioningService(
            CreatorProfileService creatorProfileService,
            ProductRepository productRepository,
            ProductVersionRepository productVersionRepository,
            FileStorageService fileStorageService,
            UploadPolicy uploadPolicy,
            ApplicationEventPublisher eventPublisher
    ) {
        this.creatorProfileService = creatorProfileService;
        this.productRepository = productRepository;
        this.productVersionRepository = productVersionRepository;
        this.fileStorageService = fileStorageService;
        this.uploadPolicy = uploadPolicy;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public CatalogDtos.ProductVersionDto publishNewVersion(
            UUID userId,
            UUID productId,
            String versionNumber,
            String changelog,
            MultipartFile patchFile
    ) throws IOException {
        CreatorProfile creator = creatorProfileService.requireApprovedCreator(userId);
        // Anti-IDOR: owner-scoped lookup
        Product product = productRepository.findOwnedBy(productId, userId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        if (product.getProductType() == ProductType.BUNDLE) {
            throw ApiException.badRequest("Atualize a versão diretamente nos patches individuais do pacote.");
        }
        if (versionNumber == null || !versionNumber.trim().matches("^\\d+\\.\\d+\\.\\d+$")) {
            throw ApiException.badRequest("Informe um número de versão SemVer válido (ex.: 1.1.0).");
        }
        uploadPolicy.validatePatch(patchFile);

        String safePatchFilename = StorageSupport.sanitizeFilename(patchFile.getOriginalFilename());
        StoredObject storedPatch;
        try (InputStream in = patchFile.getInputStream()) {
            storedPatch = fileStorageService.storePrivateFile(
                    "patches",
                    safePatchFilename,
                    in,
                    patchFile.getSize(),
                    "application/octet-stream"
            );
        }

        String trimmedVersion = versionNumber.trim();
        String effectiveChangelog = (changelog != null && !changelog.isBlank())
                ? changelog.trim() : "Atualização de patch.";

        ProductVersion version = new ProductVersion();
        version.setProduct(product);
        version.setVersionNumber(trimmedVersion);
        version.setChangelog(effectiveChangelog);
        version.setPatchFileKey(storedPatch.storageKey());
        version.setOriginalFilename(safePatchFilename);
        version.setFileSizeBytes(storedPatch.sizeBytes());
        version.setChecksumSha256(storedPatch.sha256Checksum());
        version = productVersionRepository.save(version);

        product.setCurrentVersion(trimmedVersion);
        productRepository.save(product);

        // A4: Async batched notification fan-out after transaction commit
        eventPublisher.publishEvent(new PatchVersionPublishedEvent(
                product.getId(),
                product.getTitle(),
                creator.getDisplayName(),
                trimmedVersion,
                effectiveChangelog
        ));

        return ProductVersionMapper.toDto(version);
    }
}
