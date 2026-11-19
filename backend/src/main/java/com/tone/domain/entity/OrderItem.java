package com.tone.domain.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
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

    protected OrderItem() {}

    public static OrderItem snapshot(Order order, Product product, BigDecimal takeRate, String versionOverride) {
        BigDecimal gross = product.getPrice().setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = takeRate.setScale(2, RoundingMode.HALF_UP);
        BigDecimal fee = gross.multiply(rate).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
        BigDecimal net = gross.subtract(fee).setScale(2, RoundingMode.HALF_UP);

        OrderItem oi = new OrderItem();
        oi.id = UUID.randomUUID();
        oi.order = order;
        oi.product = product;
        oi.creator = product.getCreator();
        oi.productTitleSnapshot = product.getTitle();
        oi.purchasedVersion = versionOverride != null ? versionOverride : product.getCurrentVersion();
        oi.unitPrice = gross;
        oi.takeRatePercent = rate;
        oi.platformFeeAmount = fee;
        oi.creatorNetAmount = net;
        return oi;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public Product getProduct() { return product; }
    public CreatorProfile getCreator() { return creator; }
    public String getProductTitleSnapshot() { return productTitleSnapshot; }
    public String getPurchasedVersion() { return purchasedVersion; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public BigDecimal getTakeRatePercent() { return takeRatePercent; }
    public BigDecimal getPlatformFeeAmount() { return platformFeeAmount; }
    public BigDecimal getCreatorNetAmount() { return creatorNetAmount; }
}
