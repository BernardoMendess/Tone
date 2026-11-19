package com.tone.domain.entity;

import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.PaymentMethod;
import com.tone.exception.InvalidStateTransitionException;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @Version
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

    @Column(name = "idempotency_key", length = 80)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private OrderStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_method", nullable = false, length = 20)
    private PaymentMethod paymentMethod;

    @Column(name = "total_amount", nullable = false, precision = 12, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "platform_fee_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal platformFeeTotal;

    @Column(name = "creator_net_total", nullable = false, precision = 12, scale = 2)
    private BigDecimal creatorNetTotal;

    @Column(name = "gateway_transaction_id", length = 120)
    private String gatewayTransactionId;

    @Column(name = "pix_copy_paste", columnDefinition = "TEXT")
    private String pixCopyPaste;

    @Column(name = "pix_expires_at")
    private Instant pixExpiresAt;

    @Column(name = "paid_at")
    private Instant paidAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST, orphanRemoval = false)
    private List<OrderItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "order", cascade = CascadeType.PERSIST, orphanRemoval = false)
    private List<PaymentSplit> splits = new ArrayList<>();

    protected Order() {}

    public static Order place(
            User buyer,
            String idempotencyKey,
            PaymentMethod method,
            List<CartItem> cartItems,
            BigDecimal takeRate,
            Instant now
    ) {
        if (cartItems == null || cartItems.isEmpty()) {
            throw new IllegalArgumentException("Carrinho vazio.");
        }
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.buyer = buyer;
        order.orderCode = OrderCodeGenerator.next();
        order.idempotencyKey = idempotencyKey;
        order.paymentMethod = method;
        order.status = OrderStatus.PENDING_PAYMENT;
        order.createdAt = now;
        order.updatedAt = now;

        for (CartItem ci : cartItems) {
            order.items.add(OrderItem.snapshot(order, ci.getProduct(), takeRate, null));
        }
        order.splits.addAll(PaymentSplit.aggregateByCreator(order, order.items, takeRate, now));
        order.recalculateTotals();
        return order;
    }

    public static Order seedPaid(
            User buyer,
            String orderCode,
            String gatewayTxId,
            List<Product> products,
            String ac30VersionOverride,
            BigDecimal takeRate,
            Instant paidAt
    ) {
        Order order = new Order();
        order.id = UUID.randomUUID();
        order.buyer = buyer;
        order.orderCode = orderCode;
        order.idempotencyKey = "seed-" + orderCode;
        order.paymentMethod = PaymentMethod.PIX;
        order.status = OrderStatus.PAID;
        order.gatewayTransactionId = gatewayTxId;
        order.paidAt = paidAt;
        order.createdAt = paidAt;
        order.updatedAt = paidAt;

        for (Product p : products) {
            String ver = (ac30VersionOverride != null && "ac30-worship-shimmer-pack".equals(p.getSlug()))
                    ? ac30VersionOverride
                    : p.getCurrentVersion();
            order.items.add(OrderItem.snapshot(order, p, takeRate, ver));
        }
        List<PaymentSplit> splits = PaymentSplit.aggregateByCreator(order, order.items, takeRate, paidAt);
        for (PaymentSplit s : splits) {
            s.assignGatewayReference("SPL_" + orderCode);
            s.markSettled(paidAt, paidAt);
        }
        order.splits.addAll(splits);
        order.recalculateTotals();
        return order;
    }

    private void recalculateTotals() {
        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal fee = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        for (OrderItem item : items) {
            gross = gross.add(item.getUnitPrice());
            fee = fee.add(item.getPlatformFeeAmount());
            net = net.add(item.getCreatorNetAmount());
        }
        this.totalAmount = gross.setScale(2, RoundingMode.HALF_UP);
        this.platformFeeTotal = fee.setScale(2, RoundingMode.HALF_UP);
        this.creatorNetTotal = net.setScale(2, RoundingMode.HALF_UP);
        if (this.platformFeeTotal.add(this.creatorNetTotal).compareTo(this.totalAmount) != 0) {
            throw new IllegalStateException("Invariante financeira violada no pedido " + orderCode);
        }
    }

    public void attachCharge(
            String gatewayTransactionId,
            String pixCopyPaste,
            Instant pixExpiresAt,
            Map<UUID, String> creatorSplitReferences,
            Instant now
    ) {
        if (this.status != OrderStatus.PENDING_PAYMENT) {
            throw new InvalidStateTransitionException("Order", id, status, OrderStatus.PENDING_PAYMENT);
        }
        this.gatewayTransactionId = gatewayTransactionId;
        this.pixCopyPaste = pixCopyPaste;
        this.pixExpiresAt = pixExpiresAt;
        this.updatedAt = now;
        if (creatorSplitReferences != null) {
            for (PaymentSplit split : splits) {
                String ref = creatorSplitReferences.get(split.getCreator().getId());
                if (ref != null) {
                    split.assignGatewayReference(ref);
                }
            }
        }
    }

    public void markPaid(Instant at) {
        transitionTo(OrderStatus.PAID, at);
        this.paidAt = at;
    }

    public void markFailed(Instant at) {
        transitionTo(OrderStatus.FAILED, at);
    }

    public void markExpired(Instant at) {
        transitionTo(OrderStatus.EXPIRED, at);
    }

    public void cancel(Instant at) {
        transitionTo(OrderStatus.CANCELLED, at);
    }

    public void markRefunded(Instant at) {
        transitionTo(OrderStatus.REFUNDED, at);
    }

    public boolean isPixExpired(Instant now) {
        return pixExpiresAt != null && now.isAfter(pixExpiresAt);
    }

    public boolean hasChargeAttached() {
        return gatewayTransactionId != null && !gatewayTransactionId.isBlank();
    }

    private void transitionTo(OrderStatus target, Instant at) {
        if (!this.status.canTransitionTo(target)) {
            throw new InvalidStateTransitionException("Order", id, this.status, target);
        }
        this.status = target;
        this.updatedAt = at;
    }

    @PrePersist
    void prePersist() {
        Instant now = Instant.now();
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = now;
        if (updatedAt == null) updatedAt = now;
    }

    @PreUpdate
    void preUpdate() {
        this.updatedAt = Instant.now();
    }

    public UUID getId() { return id; }
    public Long getVersion() { return version; }
    public User getBuyer() { return buyer; }
    public String getOrderCode() { return orderCode; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public OrderStatus getStatus() { return status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public BigDecimal getPlatformFeeTotal() { return platformFeeTotal; }
    public BigDecimal getCreatorNetTotal() { return creatorNetTotal; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public String getPixCopyPaste() { return pixCopyPaste; }
    public Instant getPixExpiresAt() { return pixExpiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public List<OrderItem> getItems() { return Collections.unmodifiableList(items); }
    public List<PaymentSplit> getSplits() { return Collections.unmodifiableList(splits); }
}
