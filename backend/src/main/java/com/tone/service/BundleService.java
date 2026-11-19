package com.tone.service;

import com.tone.domain.entity.BundleItem;
import com.tone.domain.entity.CreatorProfile;
import com.tone.domain.entity.Product;
import com.tone.domain.enums.ProductType;
import com.tone.dto.CatalogDtos;
import com.tone.dto.CreatorDtos;
import com.tone.exception.ApiException;
import com.tone.repository.BundleItemRepository;
import com.tone.repository.ProductRepository;
import com.tone.service.mapper.ProductCardMapper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.UUID;

@Service
public class BundleService {

    private final CreatorProfileService creatorProfileService;
    private final ProductRepository productRepository;
    private final BundleItemRepository bundleItemRepository;
    private final ProductCardMapper productCardMapper;

    public BundleService(
            CreatorProfileService creatorProfileService,
            ProductRepository productRepository,
            BundleItemRepository bundleItemRepository,
            ProductCardMapper productCardMapper
    ) {
        this.creatorProfileService = creatorProfileService;
        this.productRepository = productRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.productCardMapper = productCardMapper;
    }

    @Transactional
    @CacheEvict(cacheNames = "catalog-filters", allEntries = true)
    public CatalogDtos.ProductCardDto createBundle(UUID userId, CreatorDtos.CreateBundleRequest req) {
        CreatorProfile creator = creatorProfileService.requireApprovedCreator(userId);
        List<UUID> distinctIds = req.includedProductIds().stream().distinct().toList();
        if (distinctIds.size() < 2) {
            throw ApiException.badRequest("Selecione pelo menos 2 timbres distintos para compor um pacote.");
        }

        List<Product> included = productRepository.findAllById(distinctIds);
        if (included.size() != distinctIds.size()) {
            throw ApiException.notFound("Um ou mais timbres selecionados não foram encontrados.");
        }

        BigDecimal sumIndividual = BigDecimal.ZERO;
        for (Product p : included) {
            if (!p.getCreator().getId().equals(creator.getId())) {
                throw ApiException.forbidden("Você só pode incluir timbres da sua própria autoria em um pacote.");
            }
            if (p.getProductType() == ProductType.BUNDLE) {
                throw ApiException.badRequest("Não é permitido aninhar um pacote dentro de outro pacote.");
            }
            if (!p.isActive()) {
                throw ApiException.badRequest("Todos os timbres incluídos no pacote devem estar ativos.");
            }
            sumIndividual = sumIndividual.add(p.getPrice());
        }

        if (req.promotionalPrice().compareTo(sumIndividual) >= 0) {
            throw ApiException.badRequest(String.format(
                    "O preço promocional (R$ %.2f) deve ser menor que a soma dos itens individuais (R$ %.2f).",
                    req.promotionalPrice(), sumIndividual
            ));
        }

        Product first = included.getFirst();
        Product bundle = new Product();
        bundle.setCreator(creator);
        bundle.setTitle(req.title().trim());
        bundle.setSlug(SlugUtils.generateUnique(req.title(), productRepository::existsBySlug));
        bundle.setDescription(req.description().trim());
        bundle.setPrice(req.promotionalPrice().setScale(2, RoundingMode.HALF_UP));
        bundle.setProductType(ProductType.BUNDLE);
        bundle.setGenre(first.getGenre());
        bundle.setTargetGear(first.getTargetGear());
        bundle.setSourceGear(included.stream().map(Product::getSourceGear).distinct().limit(2)
                .reduce((a, b) -> a + " + " + b).orElse(first.getSourceGear()));
        bundle.setPickupType(first.getPickupType());
        bundle.setTags(req.tags() != null ? req.tags().trim() : "bundle,pack");
        bundle.setVideoUrl(req.videoUrl() != null && !req.videoUrl().isBlank() ? req.videoUrl().trim() : null);
        bundle.setDemoAudioKey(first.getDemoAudioKey());
        bundle.setDemoDurationSeconds(first.getDemoDurationSeconds());
        bundle.setDemoLoudnessLufs(first.getDemoLoudnessLufs());
        bundle.setDemoBitrateKbps(first.getDemoBitrateKbps());
        bundle.setCurrentVersion("1.0.0");
        bundle.setActive(true);
        bundle = productRepository.save(bundle);

        Product savedBundle = bundle;
        List<BundleItem> bundleItems = included.stream().map(p -> {
            BundleItem bi = new BundleItem();
            bi.setBundle(savedBundle);
            bi.setIncludedProduct(p);
            return bi;
        }).toList();
        bundleItemRepository.saveAll(bundleItems);

        return productCardMapper.toCard(bundle);
    }
}
