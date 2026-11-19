package com.toneguitar.domain.entity;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "order_items")
public class OrderItem {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    @Column(name = "product_title_snapshot", nullable = false, length = 180)
    private String productTitleSnapshot;

    @Column(name = "purchased_version", nullable = false, length = 30)
    private String purchasedVersion;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "take_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal takeRatePercent;

    @Column(name = "platform_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "creator_net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal creatorNetAmount;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public Product getProduct() { return product; }
    public void setProduct(Product product) { this.product = product; }
    public CreatorProfile getCreator() { return creator; }
    public void setCreator(CreatorProfile creator) { this.creator = creator; }
    public String getProductTitleSnapshot() { return productTitleSnapshot; }
    public void setProductTitleSnapshot(String productTitleSnapshot) { this.productTitleSnapshot = productTitleSnapshot; }
    public String getPurchasedVersion() { return purchasedVersion; }
    public void setPurchasedVersion(String purchasedVersion) { this.purchasedVersion = purchasedVersion; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public BigDecimal getTakeRatePercent() { return takeRatePercent; }
    public void setTakeRatePercent(BigDecimal takeRatePercent) { this.takeRatePercent = takeRatePercent; }
    public BigDecimal getPlatformFeeAmount() { return platformFeeAmount; }
    public void setPlatformFeeAmount(BigDecimal platformFeeAmount) { this.platformFeeAmount = platformFeeAmount; }
    public BigDecimal getCreatorNetAmount() { return creatorNetAmount; }
    public void setCreatorNetAmount(BigDecimal creatorNetAmount) { this.creatorNetAmount = creatorNetAmount; }
}
