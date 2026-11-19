package com.tone.domain.entity;

import com.tone.domain.enums.PayoutStatus;
import com.tone.exception.InvalidStateTransitionException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payouts")
public class Payout {

    @Id
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id", nullable = false)
    private CreatorProfile creator;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(name = "pix_key", nullable = false, length = 180)
    private String pixKey;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PayoutStatus status;

    @Column(name = "requested_at", nullable = false)
    private Instant requestedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Payout() {}

    public static Payout request(
            CreatorProfile creator,
            BigDecimal amount,
            String registeredPixKey,
            String idempotencyKey,
            Instant now
    ) {
        Payout payout = new Payout();
        payout.id = UUID.randomUUID();
        payout.creator = creator;
        payout.amount = amount.setScale(2, RoundingMode.HALF_UP);
        payout.pixKey = registeredPixKey;
        payout.idempotencyKey = idempotencyKey;
        payout.status = PayoutStatus.PROCESSING;
        payout.requestedAt = now;
        return payout;
    }

    public void markCompleted(Instant at) {
        if (this.status != PayoutStatus.PROCESSING) {
            throw new InvalidStateTransitionException("Payout", id, this.status, PayoutStatus.COMPLETED);
        }
        this.status = PayoutStatus.COMPLETED;
        this.completedAt = at;
    }

    public void markFailed() {
        if (this.status != PayoutStatus.PROCESSING) {
            throw new InvalidStateTransitionException("Payout", id, this.status, PayoutStatus.FAILED);
        }
        this.status = PayoutStatus.FAILED;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (requestedAt == null) requestedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public CreatorProfile getCreator() { return creator; }
    public BigDecimal getAmount() { return amount; }
    public String getPixKey() { return pixKey; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public PayoutStatus getStatus() { return status; }
    public Instant getRequestedAt() { return requestedAt; }
    public Instant getCompletedAt() { return completedAt; }
}
