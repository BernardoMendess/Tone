package com.tone.repository;

import com.tone.domain.entity.Order;
import com.tone.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID> {

    List<Order> findByBuyerIdOrderByCreatedAtDesc(UUID buyerId);

    Optional<Order> findByIdAndBuyerId(UUID id, UUID buyerId);

    Optional<Order> findByGatewayTransactionId(String gatewayTransactionId);

    long countByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.status = :status")
    BigDecimal sumTotalAmountByStatus(OrderStatus status);

    @Query("SELECT COALESCE(SUM(o.platformFeeTotal), 0) FROM Order o WHERE o.status = :status")
    BigDecimal sumPlatformFeeByStatus(OrderStatus status);
}
