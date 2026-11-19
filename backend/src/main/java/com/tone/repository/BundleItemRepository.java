package com.tone.repository;

import com.tone.domain.entity.BundleItem;
import com.tone.domain.enums.OrderStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BundleItemRepository extends JpaRepository<BundleItem, UUID> {

    interface BundleAggregate {
        UUID getBundleId();
        long getItemCount();
        BigDecimal getOriginalPrice();
    }

    @Query("SELECT bi FROM BundleItem bi JOIN FETCH bi.includedProduct ip JOIN FETCH ip.creator WHERE bi.bundle.id = :bundleId")
    List<BundleItem> findByBundleIdWithProducts(UUID bundleId);

    @Query("SELECT bi FROM BundleItem bi JOIN FETCH bi.includedProduct ip JOIN FETCH ip.creator WHERE bi.bundle.id IN :bundleIds")
    List<BundleItem> findByBundleIdInWithProducts(Collection<UUID> bundleIds);

    @Query("""
            SELECT bi.bundle.id AS bundleId, COUNT(bi) AS itemCount, SUM(ip.price) AS originalPrice
            FROM BundleItem bi JOIN bi.includedProduct ip
            WHERE bi.bundle.id IN :bundleIds
            GROUP BY bi.bundle.id
            """)
    List<BundleAggregate> aggregateByBundleIds(Collection<UUID> bundleIds);

    @Query("""
            SELECT COUNT(bi) FROM BundleItem bi
            WHERE bi.bundle.id = :bundleId
              AND bi.includedProduct.id NOT IN (
                  SELECT oi.product.id FROM OrderItem oi
                  WHERE oi.order.buyer.id = :buyerId AND oi.order.status = :paidStatus
              )
              AND bi.includedProduct.id NOT IN (
                  SELECT bi2.includedProduct.id FROM BundleItem bi2
                  WHERE bi2.bundle.id IN (
                      SELECT oi2.product.id FROM OrderItem oi2
                      WHERE oi2.order.buyer.id = :buyerId AND oi2.order.status = :paidStatus
                  )
              )
            """)
    long countNotOwnedItems(UUID bundleId, UUID buyerId, OrderStatus paidStatus);
}
