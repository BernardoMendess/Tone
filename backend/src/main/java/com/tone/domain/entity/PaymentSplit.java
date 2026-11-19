package com.tone.domain.entity;

import com.tone.domain.enums.SplitStatus;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_splits")
public class PaymentSplit {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    @Column(name = "gross_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal grossAmount;

    @Column(name = "platform_fee_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFeeAmount;

    @Column(name = "creator_net_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal creatorNetAmount;

    @Column(name = "take_rate_percent", nullable = false, precision = 5, scale = 2)
    private BigDecimal takeRatePercent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private SplitStatus status;

    @Column(name = "gateway_split_reference", length = 120)
    private String gatewaySplitReference;

    @Column(name = "settled_at")
    private Instant settledAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public Order getOrder() { return order; }
    public void setOrder(Order order) { this.order = order; }
    public CreatorProfile getCreator() { return creator; }
    public void setCreator(CreatorProfile creator) { this.creator = creator; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public void setGrossAmount(BigDecimal grossAmount) { this.grossAmount = grossAmount; }
    public BigDecimal getPlatformFeeAmount() { return platformFeeAmount; }
    public void setPlatformFeeAmount(BigDecimal platformFeeAmount) { this.platformFeeAmount = platformFeeAmount; }
    public BigDecimal getCreatorNetAmount() { return creatorNetAmount; }
    public void setCreatorNetAmount(BigDecimal creatorNetAmount) { this.creatorNetAmount = creatorNetAmount; }
    public BigDecimal getTakeRatePercent() { return takeRatePercent; }
    public void setTakeRatePercent(BigDecimal takeRatePercent) { this.takeRatePercent = takeRatePercent; }
    public SplitStatus getStatus() { return status; }
    public void setStatus(SplitStatus status) { this.status = status; }
    public String getGatewaySplitReference() { return gatewaySplitReference; }
    public void setGatewaySplitReference(String gatewaySplitReference) { this.gatewaySplitReference = gatewaySplitReference; }
    public Instant getSettledAt() { return settledAt; }
    public void setSettledAt(Instant settledAt) { this.settledAt = settledAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
