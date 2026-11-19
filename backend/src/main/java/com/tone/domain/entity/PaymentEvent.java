package com.tone.domain.entity;

import com.tone.domain.enums.PaymentEventType;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_events")
public class PaymentEvent {

    @Id
    private UUID id;

    @Column(name = "gateway_event_id", nullable = false, unique = true, length = 120)
    private String gatewayEventId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private PaymentEventType eventType;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 40)
    private String outcome;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    protected PaymentEvent() {}

    public static PaymentEvent record(
            String gatewayEventId,
            Order order,
            PaymentEventType eventType,
            BigDecimal amount,
            String outcome,
            Instant receivedAt
    ) {
        PaymentEvent evt = new PaymentEvent();
        evt.gatewayEventId = gatewayEventId;
        evt.order = order;
        evt.eventType = eventType;
        evt.amount = amount;
        evt.outcome = outcome;
        evt.receivedAt = receivedAt;
        return evt;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public String getGatewayEventId() { return gatewayEventId; }
    public Order getOrder() { return order; }
    public PaymentEventType getEventType() { return eventType; }
    public BigDecimal getAmount() { return amount; }
    public String getOutcome() { return outcome; }
    public Instant getReceivedAt() { return receivedAt; }
}
