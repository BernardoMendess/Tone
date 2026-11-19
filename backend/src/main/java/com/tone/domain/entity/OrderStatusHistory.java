package com.tone.domain.entity;

import com.tone.domain.enums.OrderStatus;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "order_status_history")
public class OrderStatusHistory {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    @Column(length = 255)
    private String reason;

    @Column(nullable = false, length = 80)
    private String actor;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    protected OrderStatusHistory() {}

    public static OrderStatusHistory of(
            Order order,
            OrderStatus from,
            OrderStatus to,
            String actor,
            String reason,
            Instant occurredAt
    ) {
        OrderStatusHistory h = new OrderStatusHistory();
        h.order = order;
        h.fromStatus = from;
        h.toStatus = to;
        h.actor = actor;
        h.reason = reason;
        h.occurredAt = occurredAt;
        return h;
    }

    @PrePersist
    void prePersist() {
        if (id == null) id = UUID.randomUUID();
    }

    public UUID getId() { return id; }
    public Order getOrder() { return order; }
    public OrderStatus getFromStatus() { return fromStatus; }
    public OrderStatus getToStatus() { return toStatus; }
    public String getReason() { return reason; }
    public String getActor() { return actor; }
    public Instant getOccurredAt() { return occurredAt; }
}
