package com.toneguitar.repository;

import com.toneguitar.domain.entity.OrderItem;
import com.toneguitar.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN FETCH p.creator WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(UUID orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o JOIN FETCH oi.product p JOIN FETCH p.creator " +
           "WHERE o.buyer.id = :buyerId AND o.status = :status ORDER BY o.paidAt DESC")
    List<OrderItem> findPurchasedItemsByBuyer(UUID buyerId, OrderStatus status);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o JOIN FETCH o.buyer JOIN FETCH oi.product p " +
           "WHERE oi.creator.id = :creatorId AND o.status = :status ORDER BY o.paidAt DESC")
    List<OrderItem> findSalesByCreator(UUID creatorId, OrderStatus status);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.buyer.id = :buyerId AND oi.order.status = :status " +
           "AND (oi.product.id = :productId OR oi.product.id IN (SELECT bi.bundle.id FROM BundleItem bi WHERE bi.includedProduct.id = :productId))")
    boolean hasVerifiedPurchase(UUID buyerId, UUID productId, OrderStatus status);

    @Query("SELECT DISTINCT oi.order.buyer.id FROM OrderItem oi WHERE oi.order.status = :status " +
           "AND (oi.product.id = :productId OR oi.product.id IN (SELECT bi.bundle.id FROM BundleItem bi WHERE bi.includedProduct.id = :productId))")
    List<UUID> findBuyerIdsWhoPurchasedProduct(UUID productId, OrderStatus status);
}
