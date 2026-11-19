package com.tone.config;

import com.tone.audio.AudioProcessingService;
import com.tone.audio.GuitarDemoSynthesizer;
import com.tone.audio.GuitarDemoSynthesizer.ToneProfile;
import com.tone.audio.ProcessedAudio;
import com.tone.domain.entity.*;
import com.tone.domain.enums.*;
import com.tone.repository.*;
import com.tone.storage.FileStorageService;
import com.tone.storage.StoredObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Popula o marketplace com contas de demonstração, catálogo de timbres/bundles realistas,
 * arquivos binários privados no vault e demonstrações de guitarra sintetizadas e normalizadas (-14 LUFS).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final boolean seedEnabled;
    private final UserRepository userRepository;
    private final CreatorProfileRepository creatorProfileRepository;
    private final ProductRepository productRepository;
    private final ProductVersionRepository versionRepository;
    private final BundleItemRepository bundleItemRepository;
    private final OrderRepository orderRepository;
    private final OrderItemRepository orderItemRepository;
    private final PaymentSplitRepository paymentSplitRepository;
    private final ReviewRepository reviewRepository;
    private final WishlistItemRepository wishlistItemRepository;
    private final NotificationRepository notificationRepository;
    private final PasswordEncoder passwordEncoder;
    private final GuitarDemoSynthesizer synthesizer;
    private final AudioProcessingService audioProcessingService;
    private final FileStorageService fileStorageService;

    public DataSeeder(
            @Value("${app.seed.enabled:true}") boolean seedEnabled,
            UserRepository userRepository,
            CreatorProfileRepository creatorProfileRepository,
            ProductRepository productRepository,
            ProductVersionRepository versionRepository,
            BundleItemRepository bundleItemRepository,
            OrderRepository orderRepository,
            OrderItemRepository orderItemRepository,
            PaymentSplitRepository paymentSplitRepository,
            ReviewRepository reviewRepository,
            WishlistItemRepository wishlistItemRepository,
            NotificationRepository notificationRepository,
            PasswordEncoder passwordEncoder,
            GuitarDemoSynthesizer synthesizer,
            AudioProcessingService audioProcessingService,
            FileStorageService fileStorageService
    ) {
        this.seedEnabled = seedEnabled;
        this.userRepository = userRepository;
        this.creatorProfileRepository = creatorProfileRepository;
        this.productRepository = productRepository;
        this.versionRepository = versionRepository;
        this.bundleItemRepository = bundleItemRepository;
        this.orderRepository = orderRepository;
        this.orderItemRepository = orderItemRepository;
        this.paymentSplitRepository = paymentSplitRepository;
        this.reviewRepository = reviewRepository;
        this.wishlistItemRepository = wishlistItemRepository;
        this.notificationRepository = notificationRepository;
        this.passwordEncoder = passwordEncoder;
        this.synthesizer = synthesizer;
        this.audioProcessingService = audioProcessingService;
        this.fileStorageService = fileStorageService;
    }

    @Override
    @Transactional
    public void run(String... args) throws Exception {
        if (!seedEnabled || userRepository.count() > 0) {
            return;
        }
        log.info("Inicializando dados de demonstração do Tone Marketplace...");

        // 1. Users
        User admin = createUser("admin@tone.com", "Admin@123", "Curadoria Tone", UserRole.ADMIN);
        User lucasUser = createUser("lucas@tone.com", "Creator@123", "Lucas Mendes", UserRole.CREATOR);
        User rafaelUser = createUser("rafael@tone.com", "Creator@123", "Rafael Duarte", UserRole.CREATOR);
        User camilaUser = createUser("camila@tone.com", "Creator@123", "Camila Ferraz", UserRole.CREATOR);
        User pedroUser = createUser("pedro@tone.com", "Creator@123", "Pedro Rocha", UserRole.BUYER);
        User buyer = createUser("buyer@tone.com", "Buyer@123", "João Silva", UserRole.BUYER);

        // 2. Creator Profiles (3 Approved + 1 Pending in Curation Queue for RF12)
        CreatorProfile lucas = createCreator(lucasUser, "Lucas Mendes Tone Lab", "lucas-mendes-tone-lab",
                "Engenheiro de áudio e guitarrista de sessão especializado em timbres Worship, Ambient e amplificadores boutique valvulados capturados em estúdio tratado.",
                "Line 6 Helix Floor, Quad Cortex, Vox AC30 HW2",
                "https://youtube.com/@lucasmendestonelab", "https://instagram.com/lucasmendestonelab", "https://lucastonelab.com.br",
                "lucas@tone.com", CreatorStatus.APPROVED, admin);

        CreatorProfile rafael = createCreator(rafaelUser, "RiffForge Audio", "riffforge-audio",
                "Produtor de Metal moderno e Rock pesado. Capturas cirúrgicas com microfonação dupla SM57 + Royer R-121 em gabinetes Mesa Oversized e Marshall 1960.",
                "Neural DSP Quad Cortex, Kemper Profiler Stage, Mesa Dual Rectifier",
                "https://youtube.com/@riffforgeaudio", "https://instagram.com/riffforgeaudio", "https://riffforge.audio",
                "rafael@riffforge.audio", CreatorStatus.APPROVED, admin);

        CreatorProfile camila = createCreator(camilaUser, "Vintage Valve Co.", "vintage-valve-co",
                "Foco absoluto em dinâmica de palhetada, edge-of-breakup e timbres orgânicos de Blues, Classic Rock e Fusion com amplificadores vintage dos anos 60 e 70.",
                "Fractal Axe-FX III, TONEX, Fender Twin Reverb 1965, Dumble ODS",
                "https://youtube.com/@vintagevalveco", "https://instagram.com/vintagevalveco", null,
                "camila@vintagevalve.com", CreatorStatus.APPROVED, admin);

        CreatorProfile pedroPending = createCreator(pedroUser, "Pedro Rocha Studio IRs", "pedro-rocha-irs",
                "Técnico de captação especializado em Impulse Responses de gabinetes raros Greenback 1972 e Alnico Blue em salas de madeira.",
                "Kemper, Axe-FX III, Marshall 1960AX, Neumann U87",
                "https://youtube.com/@pedrorochairs", "https://instagram.com/pedrorochairs", "https://soundcloud.com/pedrorochairs",
                "pedro.rocha@pix.com.br", CreatorStatus.PENDING, null);
        pedroPending.setPortfolioDemoUrl("https://youtube.com/watch?v=dQw4w9WgXcQ");
        creatorProfileRepository.save(pedroPending);

        // 3. Individual Tones with Synthesized & Normalized Audio Demos + Private Patch Binaries
        Product p1 = createTone(lucas, "AC30 Worship Shimmer Pack", "ac30-worship-shimmer-pack",
                "Preset completo com 8 snapshots (Clean, Edge, Drive 1, Drive 2, Lead, Ambient Swell, Dotted 8th e Shimmer Pad). Calibrado para ir direto em linha (FOH/PA) sem sobras de agudos.",
                new BigDecimal("49.90"), ProductType.PATCH, "Worship", "Line 6 Helix", "Vox AC30",
                PickupType.SINGLE_COIL, "worship,shimmer,ac30,helix,snapshots,ambient",
                "https://www.youtube.com/watch?v=5qap5aO4i9A", "1.2.0", ToneProfile.WORSHIP_SHIMMER,
                "AC30_Worship_Shimmer_v1.2.hlx");

        // Add older version v1.0.0 so RF09 version history is showcased right away!
        addOlderVersion(p1, "1.0.0", "Versão inicial com 6 snapshots para firmware Helix 3.60.", "AC30_Worship_Shimmer_v1.0.hlx");
        // Update latest version changelog
        versionRepository.findFirstByProductIdOrderByCreatedAtDesc(p1.getId()).ifPresent(v -> {
            v.setChangelog("Compatível com Helix 3.71+, novos snapshots Swell/Pad e ajuste de corte hi-cut em 6.2 kHz.");
            versionRepository.save(v);
        });

        Product p2 = createTone(lucas, "Matchless DC30 Boutique Clean", "matchless-dc30-boutique-clean",
                "Captura neural ultra-dinâmica de um Matchless DC30 1994 no canal EF86. Responde perfeitamente ao knob de volume da guitarra e recebe pedais de overdrive com headroom impecável.",
                new BigDecimal("59.90"), ProductType.CAPTURE, "Worship", "Neural DSP Quad Cortex", "Matchless DC30",
                PickupType.SINGLE_COIL, "matchless,boutique,clean,quad-cortex,ef86",
                null, "1.0.0", ToneProfile.BOUTIQUE_CLEAN, "Matchless_DC30_EF86.qcc");

        Product p3 = createTone(rafael, "Dual Rectifier Modern Metal", "dual-rectifier-modern-metal",
                "Captura do canal 3 Modern empurrado por um TS808 com corte cirúrgico nos sub-graves para afinações Drop C / Drop A. Clareza de definição em palm mutes rápidos.",
                new BigDecimal("54.90"), ProductType.CAPTURE, "Metal", "Neural DSP Quad Cortex", "Mesa Dual Rectifier",
                PickupType.HUMBUCKER, "metal,djent,rectifier,high-gain,humbucker",
                "https://www.youtube.com/watch?v=jfKfPfyJRdk", "1.1.0", ToneProfile.MODERN_METAL,
                "Mesa_DualRec_Modern_v1.1.qcc");

        Product p4 = createTone(rafael, "JCM800 Hot-Rodded Crunch", "jcm800-hot-rodded-crunch",
                "Profile de estúdio de um Marshall JCM800 2203 1982 modificado com estágio extra de ganho. Médio rasgado clássico para Hard Rock e Arena Rock.",
                new BigDecimal("44.90"), ProductType.CAPTURE, "Rock", "Kemper Profiler", "Marshall JCM800",
                PickupType.HUMBUCKER, "rock,marshall,jcm800,crunch,kemper",
                null, "1.0.0", ToneProfile.CLASSIC_CRUNCH, "JCM800_2203_HotRod.kipr");

        Product p5 = createTone(camila, "Twin Reverb 1965 Edge of Breakup", "twin-reverb-1965-edge-breakup",
                "Preset calibrado para Stratocaster e Telecaster. Limpo cristalino em palhetada suave que quebra com compressão natural valvulada ao atacar as cordas.",
                new BigDecimal("49.90"), ProductType.PATCH, "Blues", "Fractal Axe-FX III", "Fender Twin Reverb",
                PickupType.SINGLE_COIL, "blues,fender,twin-reverb,clean,breakup,fractal",
                null, "1.0.0", ToneProfile.BLUES_BREAKUP, "TwinReverb65_Breakup.syx");

        Product p6 = createTone(rafael, "Celestion V30 4x12 Studio IR Pack", "celestion-v30-4x12-studio-ir",
                "Pacote com 18 Impulse Responses (48kHz/24-bit WAV, fase mínima alinhada) capturados com SM57, MD421, Royer R-121 e mixes prontos para mixagem imediata em qualquer pedaleira.",
                new BigDecimal("39.90"), ProductType.IR, "Metal", "IR Universal (WAV)", "Mesa 4x12 Oversized V30",
                PickupType.ANY, "ir,impulse-response,v30,cab,4x12,universal",
                null, "2.0.0", ToneProfile.V30_CAB_IR, "Celestion_V30_4x12_IR_Pack_v2.zip");

        Product p7 = createTone(camila, "Dumble ODS Fusion Lead", "dumble-ods-fusion-lead",
                "Captura TONEX de um Overdrive Special estilo Robben Ford / Larry Carlton. Sustain cremoso, médios cantantes e articulação impressionante em solos de Fusion e Blues.",
                new BigDecimal("64.90"), ProductType.CAPTURE, "Fusion", "IK Multimedia TONEX", "Dumble Overdrive Special",
                PickupType.HUMBUCKER, "dumble,fusion,lead,tonex,sustain",
                null, "1.0.0", ToneProfile.DUMBLE_LEAD, "Dumble_ODS_FusionLead.txp");

        Product p8 = createTone(lucas, "JC-120 Lush Ambient Swells", "jc120-lush-ambient-swells",
                "Texturas estéreo inspiradas no clássico Roland JC-120 com chorus analógico dimensional, dual delay ping-pong e reverb hall infinito para pads e atmosferas.",
                new BigDecimal("42.90"), ProductType.PATCH, "Ambient", "Boss GT-1000", "Roland JC-120",
                PickupType.P90, "ambient,swells,jc120,chorus,boss-gt1000",
                null, "1.0.0", ToneProfile.AMBIENT_SWELLS, "JC120_Lush_Ambient.tsl");

        // 4. Promotional Bundles (RF08)
        Product bundle1 = createBundle(lucas, "Worship Ultimate Rig Bundle", "worship-ultimate-rig-bundle",
                "Coleção definitiva para guitarristas de Worship e Ambient: reúne os 3 timbres assinatura do Lucas Mendes Tone Lab com 25% de desconto exclusivo no pacote.",
                new BigDecimal("114.90"), "Worship", "Multi-Plataforma (Helix / QC / GT-1000)",
                "Vox AC30 + Matchless DC30 + JC-120", PickupType.SINGLE_COIL, "bundle,worship,promocao,ambient",
                List.of(p1, p2, p8));

        Product bundle2 = createBundle(rafael, "Heavy Studio Essentials Bundle", "heavy-studio-essentials-bundle",
                "O kit completo da RiffForge Audio para gravações e shows de Rock/Metal: Dual Rectifier + JCM800 Hot-Rod + Pack de 18 IRs Celestion V30 com 22% OFF.",
                new BigDecimal("108.90"), "Metal", "Quad Cortex / Kemper / IR WAV",
                "Dual Rectifier + JCM800 + V30 4x12", PickupType.HUMBUCKER, "bundle,metal,rock,ir,high-gain",
                List.of(p3, p4, p6));

        // 5. Seed Historical Paid Orders + Settled Splits (so Buyer Library, Reviews & Creator Financial Dashboard start alive!)
        seedPaidOrder(buyer, "TG-2026-SEED01", Instant.now().minus(18, ChronoUnit.DAYS), List.of(p1, p5), "1.0.0");
        seedPaidOrder(buyer, "TG-2026-SEED02", Instant.now().minus(5, ChronoUnit.DAYS), List.of(bundle2), "1.0.0");

        // 6. Seed Verified Reviews (RF05)
        seedReview(p1, buyer, 5, "Som absurdo direto na mesa! Os snapshots de drive e shimmer cortam a mix sem precisar mexer em quase nada.");
        seedReview(p5, buyer, 5, "Dinâmica impressionante na minha Stratocaster. Limpa totalmente só baixando o volume da guitarra.");
        seedReview(p3, buyer, 4, "Palm mutes muito definidos em Drop C. Recomendo usar com captador de saída média/alta.");

        // 7. Seed Wishlist for Buyer (RF06)
        WishlistItem wi = new WishlistItem();
        wi.setUser(buyer);
        wi.setProduct(p7);
        wishlistItemRepository.save(wi);

        WishlistItem wi2 = new WishlistItem();
        wi2.setUser(buyer);
        wi2.setProduct(bundle1);
        wishlistItemRepository.save(wi2);

        // 8. Seed Patch Update Notification for Buyer (RF09)
        Notification updateNotif = new Notification();
        updateNotif.setUser(buyer);
        updateNotif.setType(NotificationType.PATCH_UPDATE);
        updateNotif.setTitle("Atualização disponível: AC30 Worship Shimmer Pack (v1.2.0)");
        updateNotif.setMessage("Lucas Mendes Tone Lab lançou a versão 1.2.0 com suporte ao Helix 3.71+ e novos snapshots Swell/Pad. Baixe gratuitamente na sua Biblioteca Digital.");
        updateNotif.setRelatedProductId(p1.getId());
        notificationRepository.save(updateNotif);

        log.info("Tone seed concluído: 8 timbres, 2 bundles, demos normalizadas (-14 LUFS) e pedidos de exemplo prontos.");
    }

    private User createUser(String email, String rawPassword, String fullName, UserRole role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(rawPassword));
        u.setFullName(fullName);
        u.setRole(role);
        return userRepository.save(u);
    }

    private CreatorProfile createCreator(
            User user, String displayName, String slug, String bio, String mainGear,
            String yt, String ig, String web, String pixKey, CreatorStatus status, User reviewer
    ) {
        CreatorProfile c = new CreatorProfile();
        c.setUser(user);
        c.setDisplayName(displayName);
        c.setSlug(slug);
        c.setBio(bio);
        c.setMainGear(mainGear);
        c.setYoutubeUrl(yt);
        c.setInstagramUrl(ig);
        c.setWebsiteUrl(web);
        c.setPixKey(pixKey);
        c.setStatus(status);
        if (reviewer != null) {
            c.setReviewedBy(reviewer);
            c.setReviewedAt(Instant.now().minus(30, ChronoUnit.DAYS));
        }
        return creatorProfileRepository.save(c);
    }

    private Product createTone(
            CreatorProfile creator, String title, String slug, String description, BigDecimal price,
            ProductType type, String genre, String targetGear, String sourceGear, PickupType pickupType,
            String tags, String videoUrl, String versionNum, ToneProfile profile, String patchFilename
    ) throws Exception {
        byte[] rawWav = synthesizer.synthesizeDemoWav(profile);
        ProcessedAudio processed = audioProcessingService.processDemoAudio(rawWav, slug + ".wav");
        StoredObject storedDemo = fileStorageService.storePrivateFile(
                "demos", slug + "." + processed.extension(), processed.bytes(), processed.contentType()
        );

        String patchContent = String.format(
                "{\"schema\":\"TonePatchV1\",\"title\":\"%s\",\"version\":\"%s\",\"targetGear\":\"%s\",\"sourceGear\":\"%s\",\"creator\":\"%s\",\"calibratedLufs\":-14.0,\"blocks\":[\"InputGate\",\"Comp\",\"AmpModel\",\"CabIR\",\"ModChorus\",\"StereoDelay\",\"HallReverb\"]}",
                title, versionNum, targetGear, sourceGear, creator.getDisplayName()
        );
        StoredObject storedPatch = fileStorageService.storePrivateFile(
                "patches", patchFilename, patchContent.getBytes(StandardCharsets.UTF_8), "application/octet-stream"
        );

        Product p = new Product();
        p.setCreator(creator);
        p.setTitle(title);
        p.setSlug(slug);
        p.setDescription(description);
        p.setPrice(price);
        p.setProductType(type);
        p.setGenre(genre);
        p.setTargetGear(targetGear);
        p.setSourceGear(sourceGear);
        p.setPickupType(pickupType);
        p.setTags(tags);
        p.setVideoUrl(videoUrl);
        p.setDemoAudioKey(storedDemo.storageKey());
        p.setDemoDurationSeconds(processed.durationSeconds());
        p.setDemoLoudnessLufs(processed.normalizedLufs());
        p.setDemoBitrateKbps(processed.effectiveBitrateKbps());
        p.setCurrentVersion(versionNum);
        p.setActive(true);
        p = productRepository.save(p);

        ProductVersion pv = new ProductVersion();
        pv.setProduct(p);
        pv.setVersionNumber(versionNum);
        pv.setChangelog("Lançamento calibrado em estúdio.");
        pv.setPatchFileKey(storedPatch.storageKey());
        pv.setOriginalFilename(patchFilename);
        pv.setFileSizeBytes(storedPatch.sizeBytes());
        pv.setChecksumSha256(storedPatch.sha256Checksum());
        versionRepository.save(pv);

        return p;
    }

    private void addOlderVersion(Product product, String versionNum, String changelog, String filename) {
        String content = "{\"schema\":\"TonePatchV1\",\"title\":\"" + product.getTitle() + "\",\"version\":\"" + versionNum + "\"}";
        StoredObject stored = fileStorageService.storePrivateFile(
                "patches", filename, content.getBytes(StandardCharsets.UTF_8), "application/octet-stream"
        );
        ProductVersion pv = new ProductVersion();
        pv.setProduct(product);
        pv.setVersionNumber(versionNum);
        pv.setChangelog(changelog);
        pv.setPatchFileKey(stored.storageKey());
        pv.setOriginalFilename(filename);
        pv.setFileSizeBytes(stored.sizeBytes());
        pv.setChecksumSha256(stored.sha256Checksum());
        pv.setCreatedAt(Instant.now().minus(25, ChronoUnit.DAYS));
        versionRepository.save(pv);
    }

    private Product createBundle(
            CreatorProfile creator, String title, String slug, String description, BigDecimal price,
            String genre, String targetGear, String sourceGear, PickupType pickupType, String tags,
            List<Product> includedProducts
    ) {
        Product first = includedProducts.getFirst();
        Product b = new Product();
        b.setCreator(creator);
        b.setTitle(title);
        b.setSlug(slug);
        b.setDescription(description);
        b.setPrice(price);
        b.setProductType(ProductType.BUNDLE);
        b.setGenre(genre);
        b.setTargetGear(targetGear);
        b.setSourceGear(sourceGear);
        b.setPickupType(pickupType);
        b.setTags(tags);
        b.setDemoAudioKey(first.getDemoAudioKey());
        b.setDemoDurationSeconds(first.getDemoDurationSeconds());
        b.setDemoLoudnessLufs(first.getDemoLoudnessLufs());
        b.setDemoBitrateKbps(first.getDemoBitrateKbps());
        b.setCurrentVersion("1.0.0");
        b.setActive(true);
        b = productRepository.save(b);

        for (Product p : includedProducts) {
            BundleItem bi = new BundleItem();
            bi.setBundle(b);
            bi.setIncludedProduct(p);
            bundleItemRepository.save(bi);
        }
        return b;
    }

    private void seedPaidOrder(User buyer, String code, Instant paidAt, List<Product> products, String purchasedVersionOverride) {
        BigDecimal takeRate = new BigDecimal("15.00");
        BigDecimal totalGross = BigDecimal.ZERO;
        BigDecimal totalFee = BigDecimal.ZERO;
        BigDecimal totalNet = BigDecimal.ZERO;

        Order order = new Order();
        order.setBuyer(buyer);
        order.setOrderCode(code);
        order.setStatus(OrderStatus.PAID);
        order.setPaymentMethod(PaymentMethod.PIX);
        order.setGatewayTransactionId("TG_GW_SEED_" + code);
        order.setPaidAt(paidAt);
        order.setCreatedAt(paidAt);

        for (Product p : products) {
            BigDecimal g = p.getPrice();
            BigDecimal f = g.multiply(takeRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal n = g.subtract(f);
            totalGross = totalGross.add(g);
            totalFee = totalFee.add(f);
            totalNet = totalNet.add(n);
        }

        order.setTotalAmount(totalGross);
        order.setPlatformFeeTotal(totalFee);
        order.setCreatorNetTotal(totalNet);
        order = orderRepository.save(order);

        for (Product p : products) {
            BigDecimal g = p.getPrice();
            BigDecimal f = g.multiply(takeRate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            BigDecimal n = g.subtract(f);

            OrderItem oi = new OrderItem();
            oi.setOrder(order);
            oi.setProduct(p);
            oi.setCreator(p.getCreator());
            oi.setProductTitleSnapshot(p.getTitle());
            oi.setPurchasedVersion(purchasedVersionOverride != null && p.getSlug().equals("ac30-worship-shimmer-pack")
                    ? purchasedVersionOverride : p.getCurrentVersion());
            oi.setUnitPrice(g);
            oi.setTakeRatePercent(takeRate);
            oi.setPlatformFeeAmount(f);
            oi.setCreatorNetAmount(n);
            orderItemRepository.save(oi);

            PaymentSplit split = new PaymentSplit();
            split.setOrder(order);
            split.setCreator(p.getCreator());
            split.setGrossAmount(g);
            split.setPlatformFeeAmount(f);
            split.setCreatorNetAmount(n);
            split.setTakeRatePercent(takeRate);
            split.setStatus(SplitStatus.SETTLED);
            split.setGatewaySplitReference("SPL_" + code);
            split.setSettledAt(paidAt);
            split.setCreatedAt(paidAt);
            paymentSplitRepository.save(split);

            p.setSalesCount(p.getSalesCount() + 1);
            productRepository.save(p);
        }
    }

    private void seedReview(Product product, User user, int rating, String comment) {
        Review r = new Review();
        r.setProduct(product);
        r.setUser(user);
        r.setRating(rating);
        r.setComment(comment);
        r.setVerifiedPurchase(true);
        reviewRepository.save(r);

        product.setAverageRating(BigDecimal.valueOf(rating).setScale(2, RoundingMode.HALF_UP));
        product.setReviewCount(1);
        productRepository.save(product);
    }
}
