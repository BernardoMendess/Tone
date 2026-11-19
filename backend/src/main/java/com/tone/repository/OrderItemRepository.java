package com.tone.repository;

import com.tone.domain.entity.OrderItem;
import com.tone.domain.enums.OrderStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface OrderItemRepository extends JpaRepository<OrderItem, UUID> {

    interface MonthlyRevenueRow {
        int getYear();
        int getMonth();
        BigDecimal getGross();
        BigDecimal getNet();
        long getSalesCount();
    }

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN FETCH p.creator WHERE oi.order.id = :orderId")
    List<OrderItem> findByOrderIdWithProduct(UUID orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.product p JOIN FETCH p.creator WHERE oi.order.id IN :orderIds")
    List<OrderItem> findByOrderIdInWithProduct(Collection<UUID> orderIds);

    @Query("SELECT oi.product.id FROM OrderItem oi WHERE oi.order.id = :orderId")
    List<UUID> findProductIdsByOrderId(UUID orderId);

    @Query("SELECT oi FROM OrderItem oi JOIN FETCH oi.order o JOIN FETCH oi.product p JOIN FETCH p.creator " +
           "WHERE o.buyer.id = :buyerId AND o.status = :status ORDER BY o.paidAt DESC")
    List<OrderItem> findPurchasedItemsByBuyer(UUID buyerId, OrderStatus status);

    @Query(
            value = "SELECT oi FROM OrderItem oi JOIN FETCH oi.order o JOIN FETCH o.buyer " +
                    "WHERE oi.creator.id = :creatorId AND o.status = :status ORDER BY o.paidAt DESC",
            countQuery = "SELECT COUNT(oi) FROM OrderItem oi WHERE oi.creator.id = :creatorId AND oi.order.status = :status"
    )
    Page<OrderItem> findSalesByCreator(UUID creatorId, OrderStatus status, Pageable pageable);

    @Query("""
            SELECT extract(year from COALESCE(o.paidAt, o.createdAt)) AS year,
                   extract(month from COALESCE(o.paidAt, o.createdAt)) AS month,
                   SUM(oi.unitPrice) AS gross,
                   SUM(oi.creatorNetAmount) AS net,
                   COUNT(oi) AS salesCount
            FROM OrderItem oi JOIN oi.order o
            WHERE oi.creator.id = :creatorId AND o.status = :status
            GROUP BY extract(year from COALESCE(o.paidAt, o.createdAt)),
                     extract(month from COALESCE(o.paidAt, o.createdAt))
            ORDER BY 1, 2
            """)
    List<MonthlyRevenueRow> monthlyRevenueByCreator(UUID creatorId, OrderStatus status);

    @Query("SELECT COUNT(oi) FROM OrderItem oi WHERE oi.creator.id = :creatorId AND oi.order.status = :status")
    long countSalesByCreator(UUID creatorId, OrderStatus status);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.buyer.id = :buyerId AND oi.order.status = :status " +
           "AND (oi.product.id = :productId OR oi.product.id IN (SELECT bi.bundle.id FROM BundleItem bi WHERE bi.includedProduct.id = :productId))")
    boolean hasVerifiedPurchase(UUID buyerId, UUID productId, OrderStatus status);

    @Query("SELECT COUNT(oi) > 0 FROM OrderItem oi WHERE oi.order.buyer.id = :buyerId AND oi.order.status = :pendingStatus " +
           "AND (oi.product.id = :productId OR oi.product.id IN (SELECT bi.bundle.id FROM BundleItem bi WHERE bi.includedProduct.id = :productId))")
    boolean hasPendingOrder(UUID buyerId, UUID productId, OrderStatus pendingStatus);

    @Query("SELECT DISTINCT oi.order.buyer.id FROM OrderItem oi WHERE oi.order.status = :status " +
           "AND (oi.product.id = :productId OR oi.product.id IN (SELECT bi.bundle.id FROM BundleItem bi WHERE bi.includedProduct.id = :productId))")
    List<UUID> findBuyerIdsWhoPurchasedProduct(UUID productId, OrderStatus status);
}
