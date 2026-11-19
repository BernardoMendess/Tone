package com.tone.domain.entity;

import com.tone.domain.enums.SplitStatus;
import com.tone.exception.InvalidStateTransitionException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Column(name = "available_at")
    private Instant availableAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected PaymentSplit() {}

    public static List<PaymentSplit> aggregateByCreator(
            Order order,
            List<OrderItem> items,
            BigDecimal takeRate,
            Instant now
    ) {
        Map<UUID, Accumulator> byCreator = new LinkedHashMap<>();
        for (OrderItem oi : items) {
            byCreator.computeIfAbsent(oi.getCreator().getId(), k -> new Accumulator(oi.getCreator()))
                    .add(oi.getUnitPrice(), oi.getPlatformFeeAmount(), oi.getCreatorNetAmount());
        }
        List<PaymentSplit> result = new ArrayList<>(byCreator.size());
        BigDecimal rate = takeRate.setScale(2, RoundingMode.HALF_UP);
        for (Accumulator acc : byCreator.values()) {
            PaymentSplit split = new PaymentSplit();
            split.id = UUID.randomUUID();
            split.order = order;
            split.creator = acc.creator;
            split.grossAmount = acc.gross.setScale(2, RoundingMode.HALF_UP);
            split.platformFeeAmount = acc.fee.setScale(2, RoundingMode.HALF_UP);
            split.creatorNetAmount = acc.net.setScale(2, RoundingMode.HALF_UP);
            split.takeRatePercent = rate;
            split.status = SplitStatus.SCHEDULED;
            split.createdAt = now;
            result.add(split);
        }
        return result;
    }

    public void assignGatewayReference(String reference) {
        this.gatewaySplitReference = reference;
    }

    public void markSettled(Instant settledAt, Instant availableAt) {
        if (this.status != SplitStatus.SCHEDULED) {
            throw new InvalidStateTransitionException("PaymentSplit", id, this.status, SplitStatus.SETTLED);
        }
        this.status = SplitStatus.SETTLED;
        this.settledAt = settledAt;
        this.availableAt = availableAt;
    }

    public void markRefunded() {
        if (this.status != SplitStatus.SETTLED && this.status != SplitStatus.SCHEDULED) {
            throw new InvalidStateTransitionException("PaymentSplit", id, this.status, SplitStatus.REFUNDED);
        }
        this.status = SplitStatus.REFUNDED;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public CreatorProfile getCreator() { return creator; }
    public BigDecimal getGrossAmount() { return grossAmount; }
    public BigDecimal getPlatformFeeAmount() { return platformFeeAmount; }
    public BigDecimal getCreatorNetAmount() { return creatorNetAmount; }
    public BigDecimal getTakeRatePercent() { return takeRatePercent; }
    public SplitStatus getStatus() { return status; }
    public String getGatewaySplitReference() { return gatewaySplitReference; }
    public Instant getSettledAt() { return settledAt; }
    public Instant getAvailableAt() { return availableAt; }
    public Instant getCreatedAt() { return createdAt; }

    private static final class Accumulator {
        final CreatorProfile creator;
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;

        Accumulator(CreatorProfile creator) { this.creator = creator; }

        void add(BigDecimal g, BigDecimal f, BigDecimal n) {
            gross = gross.add(g);
            fee = fee.add(f);
            net = net.add(n);
        }
    }
}
