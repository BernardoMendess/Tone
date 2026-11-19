package com.tone.domain.entity;

import com.tone.domain.enums.OrderStatus;
import com.tone.domain.enums.PaymentMethod;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "buyer_id", nullable = false)
    private User buyer;

    @Column(name = "order_code", nullable = false, unique = true, length = 40)
    private String orderCode;

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

    @PrePersist
    public void prePersist() {
        if (id == null) id = UUID.randomUUID();
        if (createdAt == null) createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }
    public User getBuyer() { return buyer; }
    public void setBuyer(User buyer) { this.buyer = buyer; }
    public String getOrderCode() { return orderCode; }
    public void setOrderCode(String orderCode) { this.orderCode = orderCode; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public PaymentMethod getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(PaymentMethod paymentMethod) { this.paymentMethod = paymentMethod; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public BigDecimal getPlatformFeeTotal() { return platformFeeTotal; }
    public void setPlatformFeeTotal(BigDecimal platformFeeTotal) { this.platformFeeTotal = platformFeeTotal; }
    public BigDecimal getCreatorNetTotal() { return creatorNetTotal; }
    public void setCreatorNetTotal(BigDecimal creatorNetTotal) { this.creatorNetTotal = creatorNetTotal; }
    public String getGatewayTransactionId() { return gatewayTransactionId; }
    public void setGatewayTransactionId(String gatewayTransactionId) { this.gatewayTransactionId = gatewayTransactionId; }
    public String getPixCopyPaste() { return pixCopyPaste; }
    public void setPixCopyPaste(String pixCopyPaste) { this.pixCopyPaste = pixCopyPaste; }
    public Instant getPixExpiresAt() { return pixExpiresAt; }
    public void setPixExpiresAt(Instant pixExpiresAt) { this.pixExpiresAt = pixExpiresAt; }
    public Instant getPaidAt() { return paidAt; }
    public void setPaidAt(Instant paidAt) { this.paidAt = paidAt; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
