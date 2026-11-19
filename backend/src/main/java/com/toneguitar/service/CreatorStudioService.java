package com.toneguitar.service;

import com.toneguitar.audio.AudioProcessingService;
import com.toneguitar.audio.ProcessedAudio;
import com.toneguitar.domain.entity.*;
import com.toneguitar.domain.enums.*;
import com.toneguitar.dto.CatalogDtos;
import com.toneguitar.dto.CreatorDtos;
import com.toneguitar.exception.ApiException;
import com.toneguitar.repository.*;
import com.toneguitar.storage.FileStorageService;
import com.toneguitar.storage.StoredObject;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Módulo do Criador: RF07 (Cadastro de Produto), RF08 (Bundles), RF09 (Versionamento com notificação),
 * RF10 (Dashboard Financeiro e Saques), RF11 (Gestão de Perfil) e submissão para RF12 (Curadoria).
 */
@Service
public class CreatorStudioService {

    private final CreatorProfileRepository creatorProfileRepository;
    private final UserRepository userRepository;
    private final ProductRepository productRepository;
    private final ProductVersionRepository productVersionRepository;
    private final BundleItemRepository bundleItemRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final PayoutRepository payoutRepository;
    private final NotificationRepository notificationRepository;
    private final FileStorageService fileStorageService;
    private final AudioProcessingService audioProcessingService;
    private final PlatformSettingsService platformSettingsService;
    private final CatalogService catalogService;

    public CreatorStudioService(
            CreatorProfileRepository creatorProfileRepository,
            UserRepository userRepository,
            ProductRepository productRepository,
            ProductVersionRepository productVersionRepository,
            BundleItemRepository bundleItemRepository,
            OrderItemRepository orderItemRepository,
            PaymentSplitRepository paymentSplitRepository,
            PayoutRepository payoutRepository,
            NotificationRepository notificationRepository,
            FileStorageService fileStorageService,
            AudioProcessingService audioProcessingService,
            PlatformSettingsService platformSettingsService,
            CatalogService catalogService
    ) {
        this.creatorProfileRepository = creatorProfileRepository;
        this.userRepository = userRepository;
        this.productRepository = productRepository;
        this.productVersionRepository = productVersionRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.payoutRepository = payoutRepository;
        this.notificationRepository = notificationRepository;
        this.fileStorageService = fileStorageService;
        this.audioProcessingService = audioProcessingService;
        this.platformSettingsService = platformSettingsService;
        this.catalogService = catalogService;
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto applyAsCreator(UUID userId, CreatorDtos.CreatorApplicationRequest req) {
        Optional<CreatorProfile> existingOpt = creatorProfileRepository.findByUserId(userId);
        if (existingOpt.isPresent()) {
            CreatorProfile existing = existingOpt.get();
            if (existing.getStatus() == CreatorStatus.APPROVED) {
                throw ApiException.conflict("Seu perfil de criador já está aprovado.");
            }
            if (existing.getStatus() == CreatorStatus.PENDING) {
                throw ApiException.conflict("Sua solicitação já está em análise pela curadoria.");
            }
            // Allow re-applying if previously rejected
            existing.setDisplayName(req.displayName().trim());
            existing.setBio(req.bio().trim());
            existing.setMainGear(req.mainGear().trim());
            existing.setPortfolioDemoUrl(req.portfolioDemoUrl());
            existing.setYoutubeUrl(req.youtubeUrl());
            existing.setInstagramUrl(req.instagramUrl());
            existing.setWebsiteUrl(req.websiteUrl());
            existing.setPixKey(req.pixKey().trim());
            existing.setStatus(CreatorStatus.PENDING);
            existing.setRejectionReason(null);
            return toProfileDto(creatorProfileRepository.save(existing));
        }

        User user = userRepository.findById(userId)
                .orElseThrow(() -> ApiException.notFound("Usuário não encontrado."));

        CreatorProfile profile = new CreatorProfile();
        profile.setUser(user);
        profile.setDisplayName(req.displayName().trim());
        profile.setSlug(generateUniqueCreatorSlug(req.displayName()));
        profile.setBio(req.bio().trim());
        profile.setMainGear(req.mainGear().trim());
        profile.setPortfolioDemoUrl(req.portfolioDemoUrl());
        profile.setYoutubeUrl(req.youtubeUrl());
        profile.setInstagramUrl(req.instagramUrl());
        profile.setWebsiteUrl(req.websiteUrl());
        profile.setPixKey(req.pixKey().trim());
        profile.setStatus(CreatorStatus.PENDING);
        return toProfileDto(creatorProfileRepository.save(profile));
    }

    @Transactional(readOnly = true)
    public CreatorDtos.CreatorProfileDto getMyCreatorProfile(UUID userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.notFound("Você ainda não possui perfil de criador."));
        return toProfileDto(profile);
    }

    @Transactional
    public CreatorDtos.CreatorProfileDto updateMyProfile(UUID userId, CreatorDtos.UpdateCreatorProfileRequest req) {
        CreatorProfile profile = requireApprovedCreator(userId);
        profile.setDisplayName(req.displayName().trim());
        if (req.slug() != null && !req.slug().isBlank() && !req.slug().equals(profile.getSlug())) {
            if (creatorProfileRepository.existsBySlug(req.slug())) {
                throw ApiException.conflict("Este slug já está em uso por outro criador.");
            }
            profile.setSlug(req.slug().trim());
        }
        profile.setBio(req.bio());
        profile.setAvatarUrl(req.avatarUrl());
        profile.setMainGear(req.mainGear());
        profile.setYoutubeUrl(req.youtubeUrl());
        profile.setInstagramUrl(req.instagramUrl());
        profile.setWebsiteUrl(req.websiteUrl());
        profile.setPixKey(req.pixKey().trim());
        return toProfileDto(creatorProfileRepository.save(profile));
    }

    /**
     * RF07 - Cadastro de Produto com upload de arquivo binário privado (RNF02) e áudio demo
     * processado com normalização -14 LUFS (RNF03) e proteção anti-ripping (RNF04).
     */
    @Transactional
    public CatalogDtos.ProductCardDto createProduct(
            UUID userId,
            String title,
            String description,
            BigDecimal price,
            ProductType productType,
            String genre,
            String targetGear,
            String sourceGear,
            PickupType pickupType,
            String tags,
            String videoUrl,
            String initialVersion,
            MultipartFile patchFile,
            MultipartFile demoAudioFile
    ) throws IOException {
        CreatorProfile creator = requireApprovedCreator(userId);

        if (productType == ProductType.BUNDLE) {
            throw ApiException.badRequest("Use o endpoint de Pacotes (Bundles) para criar pacotes promocionais.");
        }
        if (price == null || price.compareTo(new BigDecimal("1.00")) < 0) {
            throw ApiException.badRequest("Informe um preço válido (mínimo R$ 1,00).");
        }
        if (patchFile == null || patchFile.isEmpty()) {
            throw ApiException.badRequest("O arquivo binário do patch/IR é obrigatório.");
        }
        if (demoAudioFile == null || demoAudioFile.isEmpty()) {
            throw ApiException.badRequest("O arquivo de áudio de demonstração é obrigatório.");
        }

        // Process demo audio: RNF03 (LUFS normalization) + RNF04 (anti-IR-ripping compression)
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

        // Store binary patch strictly in private storage (RNF02)
        StoredObject storedPatch = fileStorageService.storePrivateFile(
                "patches",
                patchFile.getOriginalFilename() != null ? patchFile.getOriginalFilename() : "patch.bin",
                patchFile.getBytes(),
                patchFile.getContentType()
        );

        String versionLabel = (initialVersion == null || initialVersion.isBlank()) ? "1.0.0" : initialVersion.trim();

        Product product = new Product();
        product.setCreator(creator);
        product.setTitle(title.trim());
        product.setSlug(generateUniqueProductSlug(title));
        product.setDescription(description.trim());
        product.setPrice(price.setScale(2, RoundingMode.HALF_UP));
        product.setProductType(productType);
        product.setGenre(genre.trim());
        product.setTargetGear(targetGear.trim());
        product.setSourceGear(sourceGear.trim());
        product.setPickupType(pickupType);
        product.setTags(tags != null ? tags.trim() : "");
        product.setVideoUrl(videoUrl != null && !videoUrl.isBlank() ? videoUrl.trim() : null);
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

        return catalogService.toProductCard(product);
    }

    /**
     * RF08 - Criação de Pacotes (Bundles) agrupando múltiplos patches do criador com preço promocional.
     */
    @Transactional
    public CatalogDtos.ProductCardDto createBundle(UUID userId, CreatorDtos.CreateBundleRequest req) {
        CreatorProfile creator = requireApprovedCreator(userId);
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
        bundle.setSlug(generateUniqueProductSlug(req.title()));
        bundle.setDescription(req.description().trim());
        bundle.setPrice(req.promotionalPrice().setScale(2, RoundingMode.HALF_UP));
        bundle.setProductType(ProductType.BUNDLE);
        bundle.setGenre(first.getGenre());
        bundle.setTargetGear(first.getTargetGear());
        bundle.setSourceGear(included.stream().map(Product::getSourceGear).distinct().limit(2)
                .reduce((a, b) -> a + " + " + b).orElse(first.getSourceGear()));
        bundle.setPickupType(first.getPickupType());
        bundle.setTags(req.tags() != null ? req.tags() : "bundle,pack");
        bundle.setVideoUrl(req.videoUrl());
        bundle.setDemoAudioKey(first.getDemoAudioKey());
        bundle.setDemoDurationSeconds(first.getDemoDurationSeconds());
        bundle.setDemoLoudnessLufs(first.getDemoLoudnessLufs());
        bundle.setDemoBitrateKbps(first.getDemoBitrateKbps());
        bundle.setCurrentVersion("1.0.0");
        bundle.setActive(true);
        bundle = productRepository.save(bundle);

        for (Product p : included) {
            BundleItem bi = new BundleItem();
            bi.setBundle(bundle);
            bi.setIncludedProduct(p);
            bundleItemRepository.save(bi);
        }

        return catalogService.toProductCard(bundle);
    }

    /**
     * RF09 - Versionamento de Patches: atualiza binário, registra changelog, atualiza currentVersion
     * e notifica todos os compradores verificados (inclusive compradores de bundles contendo este patch).
     */
    @Transactional
    public CatalogDtos.ProductVersionDto publishNewVersion(
            UUID userId,
            UUID productId,
            String versionNumber,
            String changelog,
            MultipartFile patchFile
    ) throws IOException {
        CreatorProfile creator = requireApprovedCreator(userId);
        Product product = productRepository.findByIdWithCreator(productId)
                .orElseThrow(() -> ApiException.notFound("Timbre não encontrado."));

        if (!product.getCreator().getId().equals(creator.getId())) {
            throw ApiException.forbidden("Você não tem permissão para atualizar este timbre.");
        }
        if (product.getProductType() == ProductType.BUNDLE) {
            throw ApiException.badRequest("Atualize a versão diretamente nos patches individuais do pacote.");
        }
        if (patchFile == null || patchFile.isEmpty()) {
            throw ApiException.badRequest("Envie o novo arquivo binário do patch.");
        }
        if (versionNumber == null || versionNumber.isBlank()) {
            throw ApiException.badRequest("Informe o número da nova versão (ex.: 1.1.0).");
        }

        StoredObject storedPatch = fileStorageService.storePrivateFile(
                "patches",
                patchFile.getOriginalFilename() != null ? patchFile.getOriginalFilename() : "patch.bin",
                patchFile.getBytes(),
                patchFile.getContentType()
        );

        ProductVersion version = new ProductVersion();
        version.setProduct(product);
        version.setVersionNumber(versionNumber.trim());
        version.setChangelog(changelog != null ? changelog.trim() : "Atualização de patch.");
        version.setPatchFileKey(storedPatch.storageKey());
        version.setOriginalFilename(patchFile.getOriginalFilename() != null ? patchFile.getOriginalFilename() : "patch.bin");
        version.setFileSizeBytes(storedPatch.sizeBytes());
        version.setChecksumSha256(storedPatch.sha256Checksum());
        version = productVersionRepository.save(version);

        product.setCurrentVersion(versionNumber.trim());
        productRepository.save(product);

        // Notify all verified buyers (direct buyers + bundle buyers)
        List<UUID> buyerIds = orderItemRepository.findBuyerIdsWhoPurchasedProduct(product.getId(), OrderStatus.PAID);
        for (UUID buyerId : buyerIds) {
            Notification notif = new Notification();
            notif.setUser(userRepository.getReferenceById(buyerId));
            notif.setType(NotificationType.PATCH_UPDATE);
            notif.setTitle("Atualização disponível: " + product.getTitle() + " (v" + version.getVersionNumber() + ")");
            notif.setMessage("O criador " + creator.getDisplayName() + " lançou a versão " + version.getVersionNumber()
                    + ". Novidades: " + version.getChangelog() + " — Já liberado na sua Biblioteca Digital.");
            notif.setRelatedProductId(product.getId());
            notificationRepository.save(notif);
        }

        return new CatalogDtos.ProductVersionDto(
                version.getId(),
                version.getVersionNumber(),
                version.getChangelog(),
                version.getOriginalFilename(),
                version.getFileSizeBytes(),
                version.getCreatedAt()
        );
    }

    @Transactional(readOnly = true)
    public List<CatalogDtos.ProductCardDto> getMyProducts(UUID userId) {
        CreatorProfile creator = requireApprovedCreator(userId);
        return productRepository.findByCreatorIdOrderByCreatedAtDesc(creator.getId()).stream()
                .map(catalogService::toProductCard)
                .toList();
    }

    /**
     * RF10 - Dashboard Financeiro do Criador (vendas, receita bruta, taxa retida, saldo líquido e saques).
     */
    @Transactional(readOnly = true)
    public CreatorDtos.CreatorDashboardDto getFinancialDashboard(UUID userId) {
        CreatorProfile creator = requireApprovedCreator(userId);
        List<OrderItem> sales = orderItemRepository.findSalesByCreator(creator.getId(), OrderStatus.PAID);
        List<Product> products = productRepository.findByCreatorIdOrderByCreatedAtDesc(creator.getId());

        BigDecimal gross = paymentSplitRepository.sumGrossByCreatorAndStatus(creator.getId(), SplitStatus.SETTLED)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = paymentSplitRepository.sumPlatformFeeByCreatorAndStatus(creator.getId(), SplitStatus.SETTLED)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal net = paymentSplitRepository.sumNetByCreatorAndStatus(creator.getId(), SplitStatus.SETTLED)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal withdrawn = payoutRepository.sumAmountByCreatorAndStatusIn(
                        creator.getId(), List.of(PayoutStatus.PROCESSING, PayoutStatus.COMPLETED))
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal available = net.subtract(withdrawn).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP);

        DateTimeFormatter ymFormatter = DateTimeFormatter.ofPattern("yyyy-MM").withZone(ZoneOffset.UTC);
        Map<String, MonthAcc> monthlyMap = new TreeMap<>();
        List<CreatorDtos.SaleRecordDto> recentSales = new ArrayList<>();

        for (OrderItem oi : sales) {
            Instant paidAt = oi.getOrder().getPaidAt() != null ? oi.getOrder().getPaidAt() : oi.getOrder().getCreatedAt();
            String month = ymFormatter.format(paidAt);
            monthlyMap.computeIfAbsent(month, k -> new MonthAcc()).add(oi.getUnitPrice(), oi.getCreatorNetAmount());

            recentSales.add(new CreatorDtos.SaleRecordDto(
                    oi.getOrder().getId(),
                    oi.getOrder().getOrderCode(),
                    oi.getProductTitleSnapshot(),
                    oi.getOrder().getBuyer().getFullName(),
                    oi.getUnitPrice(),
                    oi.getPlatformFeeAmount(),
                    oi.getCreatorNetAmount(),
                    oi.getTakeRatePercent(),
                    paidAt
            ));
        }

        List<CreatorDtos.MonthlyRevenuePoint> series = monthlyMap.entrySet().stream()
                .map(e -> new CreatorDtos.MonthlyRevenuePoint(
                        e.getKey(),
                        e.getValue().gross.setScale(2, RoundingMode.HALF_UP),
                        e.getValue().net.setScale(2, RoundingMode.HALF_UP),
                        e.getValue().count
                ))
                .toList();

        List<CreatorDtos.PayoutDto> payouts = payoutRepository.findByCreatorIdOrderByRequestedAtDesc(creator.getId())
                .stream()
                .map(p -> new CreatorDtos.PayoutDto(
                        p.getId(), p.getAmount(), p.getPixKey(), p.getStatus(), p.getRequestedAt(), p.getCompletedAt()
                ))
                .toList();

        return new CreatorDtos.CreatorDashboardDto(
                sales.size(),
                (int) products.stream().filter(Product::isActive).count(),
                gross,
                fee,
                net,
                withdrawn,
                available,
                platformSettingsService.getTakeRatePercent(),
                series,
                recentSales,
                payouts
        );
    }

    @Transactional
    public CreatorDtos.PayoutDto requestPayout(UUID userId, CreatorDtos.RequestPayoutRequest req) {
        CreatorProfile creator = requireApprovedCreator(userId);
        BigDecimal net = paymentSplitRepository.sumNetByCreatorAndStatus(creator.getId(), SplitStatus.SETTLED);
        BigDecimal withdrawn = payoutRepository.sumAmountByCreatorAndStatusIn(
                creator.getId(), List.of(PayoutStatus.PROCESSING, PayoutStatus.COMPLETED));
        BigDecimal available = net.subtract(withdrawn);

        if (req.amount().compareTo(available) > 0) {
            throw ApiException.badRequest(String.format(
                    "Saldo insuficiente para saque. Disponível: R$ %.2f.", available
            ));
        }
        String pixKey = (req.pixKey() != null && !req.pixKey().isBlank()) ? req.pixKey().trim() : creator.getPixKey();
        if (pixKey == null || pixKey.isBlank()) {
            throw ApiException.badRequest("Informe uma chave PIX válida para receber o saque.");
        }

        Payout payout = new Payout();
        payout.setCreator(creator);
        payout.setAmount(req.amount().setScale(2, RoundingMode.HALF_UP));
        payout.setPixKey(pixKey);
        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setCompletedAt(Instant.now());
        payout = payoutRepository.save(payout);

        return new CreatorDtos.PayoutDto(
                payout.getId(),
                payout.getAmount(),
                payout.getPixKey(),
                payout.getStatus(),
                payout.getRequestedAt(),
                payout.getCompletedAt()
        );
    }

    public CreatorProfile requireApprovedCreator(UUID userId) {
        CreatorProfile profile = creatorProfileRepository.findByUserId(userId)
                .orElseThrow(() -> ApiException.forbidden("Você precisa se candidatar e ser aprovado como criador."));
        if (profile.getStatus() != CreatorStatus.APPROVED) {
            throw ApiException.forbidden("Seu perfil de criador ainda não foi aprovado pela curadoria da plataforma.");
        }
        return profile;
    }

    public CreatorDtos.CreatorProfileDto toProfileDto(CreatorProfile c) {
        return new CreatorDtos.CreatorProfileDto(
                c.getId(),
                c.getUser().getId(),
                c.getUser().getFullName(),
                c.getUser().getEmail(),
                c.getDisplayName(),
                c.getSlug(),
                c.getBio(),
                c.getAvatarUrl(),
                c.getMainGear(),
                c.getYoutubeUrl(),
                c.getInstagramUrl(),
                c.getWebsiteUrl(),
                c.getPortfolioDemoUrl(),
                c.getPixKey(),
                c.getStatus(),
                c.getRejectionReason(),
                c.getReviewedAt(),
                c.getCreatedAt()
        );
    }

    private String generateUniqueCreatorSlug(String name) {
        String base = SlugUtils.toSlug(name);
        String candidate = base;
        int i = 2;
        while (creatorProfileRepository.existsBySlug(candidate)) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    private String generateUniqueProductSlug(String title) {
        String base = SlugUtils.toSlug(title);
        String candidate = base;
        int i = 2;
        while (productRepository.existsBySlug(candidate)) {
            candidate = base + "-" + i++;
        }
        return candidate;
    }

    private static class MonthAcc {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        int count = 0;

        void add(BigDecimal g, BigDecimal n) {
            gross = gross.add(g);
            net = net.add(n);
            count++;
        }
    }
}
