package com.tone.repository;

import com.tone.domain.entity.Order;
import com.tone.domain.enums.OrderStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    Page<Order> findByBuyerId(UUID buyerId, Pageable pageable);

    Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

    Optional<Order> findByBuyerIdAndIdempotencyKey(UUID buyerId, String idempotencyKey);

    Optional<Order> findByGatewayTransactionId(String gatewayTransactionId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.gatewayTransactionId = :txId")
    Optional<Order> findByGatewayTransactionIdForUpdate(String txId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.id = :id")
    Optional<Order> findByIdForUpdate(UUID id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Order o
            SET o.status = :expired, o.version = o.version + 1, o.updatedAt = :now
            WHERE o.status = :pending AND o.pixExpiresAt IS NOT NULL AND o.pixExpiresAt < :now
            """)
    int expirePendingPix(Instant now, OrderStatus pending, OrderStatus expired);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
    BigDecimal sumTotalAmountByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.platformFeeTotal), 0) FROM Order o WHERE o.status = :status")
    BigDecimal sumPlatformFeeByStatus(OrderStatus status);

    List<Order> findByStatusAndPixExpiresAtBefore(OrderStatus status, Instant cutoff);
}
