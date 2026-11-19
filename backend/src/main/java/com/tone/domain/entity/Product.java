package com.tone.domain.entity;

import com.tone.domain.enums.CreatorStatus;
import com.tone.domain.enums.PickupType;
import com.tone.domain.enums.ProductType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "products")
public class Product {

    @Id
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    @Column(nullable = false, length = 180)
    private String title;

    @Column(nullable = false, unique = true, length = 200)
    private String slug;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", nullable = false, length = 20)
    private ProductType productType;

    @Column(nullable = false, length = 80)
    private String genre;

    @Column(name = "target_gear", nullable = false, length = 120)
    private String targetGear;

    @Column(name = "source_gear", nullable = false, length = 120)
    private String sourceGear;

    @Enumerated(EnumType.STRING)
    @Column(name = "pickup_type", nullable = false, length = 30)
    private PickupType pickupType;

    @Column(length = 500)
    private String tags;

    @Column(name = "video_url", length = 500)
    private String videoUrl;

    @Column(name = "demo_audio_key", length = 500)
    private String demoAudioKey;

    @Column(name = "demo_duration_seconds")
    private Integer demoDurationSeconds = 0;

    @Column(name = "demo_loudness_lufs", precision = 5, scale = 2)
    private BigDecimal demoLoudnessLufs = new BigDecimal("-14.00");

    @Column(name = "demo_bitrate_kbps")
    private Integer demoBitrateKbps = 96;

    @Column(name = "current_version", nullable = false, length = 30)
    private String currentVersion = "1.0.0";

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "average_rating", nullable = false, precision = 3, scale = 2)
    private BigDecimal averageRating = BigDecimal.ZERO.setScale(2);

    @Column(name = "review_count", nullable = false)
    private int reviewCount = 0;

    @Column(name = "sales_count", nullable = false)
    private int salesCount = 0;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        Instant now = Instant.now();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
        if (currentVersion == null) currentVersion = "1.0.0";
        if (averageRating == null) averageRating = BigDecimal.ZERO.setScale(2);
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public boolean isPurchasable() {
        return active && creator != null && creator.getStatus() == CreatorStatus.APPROVED;
    }

    public boolean isOwnedBy(UUID userId) {
        return userId != null && creator != null && creator.getUser() != null
                && userId.equals(creator.getUser().getId());
    }

    public void deactivate() {
        this.active = false;
    }

    public void updateListing(
            String title,
            String description,
            BigDecimal price,
            String genre,
            String targetGear,
            String sourceGear,
            PickupType pickupType,
            String tags,
            String videoUrl,
            Boolean active
    ) {
        if (title != null && !title.isBlank()) this.title = title.trim();
        if (description != null && !description.isBlank()) this.description = description.trim();
        if (price != null) this.price = price.setScale(2, RoundingMode.HALF_UP);
        if (genre != null && !genre.isBlank()) this.genre = genre.trim();
        if (targetGear != null && !targetGear.isBlank()) this.targetGear = targetGear.trim();
        if (sourceGear != null && !sourceGear.isBlank()) this.sourceGear = sourceGear.trim();
        if (pickupType != null) this.pickupType = pickupType;
        if (tags != null) this.tags = tags.trim();
        if (videoUrl != null) this.videoUrl = videoUrl.isBlank() ? null : videoUrl.trim();
        if (active != null) this.active = active;
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Long getVersion() { return version; }
    public CreatorProfile getCreator() { return creator; }
    public void setCreator(CreatorProfile creator) { this.creator = creator; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
    public BigDecimal getPrice() { return price; }
    public void setPrice(BigDecimal price) { this.price = price; }
    public ProductType getProductType() { return productType; }
    public void setProductType(ProductType productType) { this.productType = productType; }
    public String getGenre() { return genre; }
    public void setGenre(String genre) { this.genre = genre; }
    public String getTargetGear() { return targetGear; }
    public void setTargetGear(String targetGear) { this.targetGear = targetGear; }
    public String getSourceGear() { return sourceGear; }
    public void setSourceGear(String sourceGear) { this.sourceGear = sourceGear; }
    public PickupType getPickupType() { return pickupType; }
    public void setPickupType(PickupType pickupType) { this.pickupType = pickupType; }
    public String getTags() { return tags; }
    public void setTags(String tags) { this.tags = tags; }
    public String getVideoUrl() { return videoUrl; }
    public void setVideoUrl(String videoUrl) { this.videoUrl = videoUrl; }
    public String getDemoAudioKey() { return demoAudioKey; }
    public void setDemoAudioKey(String demoAudioKey) { this.demoAudioKey = demoAudioKey; }
    public Integer getDemoDurationSeconds() { return demoDurationSeconds; }
    public void setDemoDurationSeconds(Integer demoDurationSeconds) { this.demoDurationSeconds = demoDurationSeconds; }
    public BigDecimal getDemoLoudnessLufs() { return demoLoudnessLufs; }
    public void setDemoLoudnessLufs(BigDecimal demoLoudnessLufs) { this.demoLoudnessLufs = demoLoudnessLufs; }
    public Integer getDemoBitrateKbps() { return demoBitrateKbps; }
    public void setDemoBitrateKbps(Integer demoBitrateKbps) { this.demoBitrateKbps = demoBitrateKbps; }
    public String getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(String currentVersion) { this.currentVersion = currentVersion; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
    public BigDecimal getAverageRating() { return averageRating; }
    public void setAverageRating(BigDecimal averageRating) { this.averageRating = averageRating; }
    public int getReviewCount() { return reviewCount; }
    public void setReviewCount(int reviewCount) { this.reviewCount = reviewCount; }
    public int getSalesCount() { return salesCount; }
    public void setSalesCount(int salesCount) { this.salesCount = salesCount; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
